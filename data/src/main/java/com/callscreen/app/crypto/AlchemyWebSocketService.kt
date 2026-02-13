package com.callscreen.app.crypto

import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.repository.CryptoRepository
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlchemyWebSocketService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cryptoRepository: CryptoRepository
) : Web3Service {

    companion object {
        private const val ALCHEMY_WS_BASE = "wss://eth-mainnet.g.alchemy.com/v2/"
        private const val CONFIRMATION_POLL_INTERVAL = 15L // seconds
        private const val TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
    }

    private val activeMonitors = ConcurrentHashMap<String, MonitorState>()

    private data class MonitorState(
        val challenge: CryptoPaymentChallenge,
        val webSocket: WebSocket?,
        val confirmationPoller: Disposable? = null,
        var subscriptionId: String? = null
    )

    override fun monitorPayment(challenge: CryptoPaymentChallenge): Flowable<Web3Service.PaymentEvent> {
        return Flowable.create({ emitter ->
            val apiKey = cryptoRepository.getAlchemyApiKey()
            if (apiKey.isBlank()) {
                emitter.onError(IllegalStateException("Alchemy API key not configured"))
                return@create
            }

            val wsUrl = "$ALCHEMY_WS_BASE$apiKey"
            val request = Request.Builder().url(wsUrl).build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Timber.d("WebSocket connected for challenge ${challenge.id}")
                    subscribeToPayments(webSocket, challenge)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        handleMessage(text, challenge, emitter)
                    } catch (e: Exception) {
                        Timber.w(e, "Error handling WebSocket message")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Timber.w(t, "WebSocket failure for challenge ${challenge.id}")
                    if (!emitter.isCancelled) {
                        emitter.onError(t)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Timber.d("WebSocket closed for challenge ${challenge.id}: $reason")
                    if (!emitter.isCancelled) {
                        emitter.onComplete()
                    }
                }
            }

            val webSocket = okHttpClient.newWebSocket(request, listener)

            activeMonitors[challenge.id] = MonitorState(challenge, webSocket)

            emitter.setCancellable {
                stopMonitoring(challenge.id)
            }
        }, BackpressureStrategy.LATEST)
    }

    private fun subscribeToPayments(webSocket: WebSocket, challenge: CryptoPaymentChallenge) {
        when (challenge.tokenType) {
            CryptoPaymentChallenge.TokenType.ETH -> subscribeEthTransactions(webSocket, challenge)
            CryptoPaymentChallenge.TokenType.USDC,
            CryptoPaymentChallenge.TokenType.USDT -> subscribeErc20Transfers(webSocket, challenge)
        }
    }

    /**
     * Subscribe to pending ETH transactions filtered by recipient address.
     */
    private fun subscribeEthTransactions(webSocket: WebSocket, challenge: CryptoPaymentChallenge) {
        val params = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "eth_subscribe")
            put("params", JSONArray().apply {
                put("alchemy_pendingTransactions")
                put(JSONObject().apply {
                    put("toAddress", challenge.walletAddress)
                    put("hashesOnly", false)
                })
            })
        }
        webSocket.send(params.toString())
        Timber.d("Subscribed to ETH transactions for ${challenge.walletAddress}")
    }

    /**
     * Subscribe to ERC-20 Transfer event logs for USDC/USDT.
     */
    private fun subscribeErc20Transfers(webSocket: WebSocket, challenge: CryptoPaymentChallenge) {
        val contractAddress = challenge.tokenType.contractAddress ?: return
        val recipientTopic = "0x000000000000000000000000${challenge.walletAddress.removePrefix("0x")}"

        val params = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "eth_subscribe")
            put("params", JSONArray().apply {
                put("logs")
                put(JSONObject().apply {
                    put("address", contractAddress)
                    put("topics", JSONArray().apply {
                        put(TRANSFER_TOPIC)
                        put(JSONObject.NULL) // any from address
                        put(recipientTopic) // to our wallet
                    })
                })
            })
        }
        webSocket.send(params.toString())
        Timber.d("Subscribed to ${challenge.tokenType.name} transfers for ${challenge.walletAddress}")
    }

    private fun handleMessage(
        text: String,
        challenge: CryptoPaymentChallenge,
        emitter: io.reactivex.FlowableEmitter<Web3Service.PaymentEvent>
    ) {
        val json = JSONObject(text)

        // Handle subscription confirmation
        if (json.has("result") && json.optInt("id") == 1) {
            val subscriptionId = json.getString("result")
            activeMonitors[challenge.id]?.let {
                activeMonitors[challenge.id] = it.copy(subscriptionId = subscriptionId)
            }
            Timber.d("Subscription confirmed: $subscriptionId")
            return
        }

        // Handle subscription events
        val params = json.optJSONObject("params") ?: return
        val result = params.optJSONObject("result") ?: return

        when (challenge.tokenType) {
            CryptoPaymentChallenge.TokenType.ETH -> {
                handleEthTransaction(result, challenge, emitter)
            }
            CryptoPaymentChallenge.TokenType.USDC,
            CryptoPaymentChallenge.TokenType.USDT -> {
                handleErc20Transfer(result, challenge, emitter)
            }
        }
    }

    private fun handleEthTransaction(
        tx: JSONObject,
        challenge: CryptoPaymentChallenge,
        emitter: io.reactivex.FlowableEmitter<Web3Service.PaymentEvent>
    ) {
        val valueHex = tx.optString("value", "0x0")
        val txHash = tx.optString("hash", "")

        // Parse wei value and compare to challenge amount
        val weiValue = BigInteger(valueHex.removePrefix("0x"), 16)
        val ethValue = BigDecimal(weiValue).divide(BigDecimal.TEN.pow(18))
        val expectedAmount = BigDecimal(challenge.exactAmount)

        if (ethValue.compareTo(expectedAmount) == 0) {
            Timber.d("ETH payment matched! tx=$txHash amount=$ethValue")
            emitter.onNext(Web3Service.PaymentEvent(
                challengeId = challenge.id,
                txHash = txHash,
                status = CryptoPaymentChallenge.PaymentStatus.CONFIRMING,
                confirmations = 0
            ))
            startConfirmationPolling(challenge.id, txHash, emitter)
        }
    }

    private fun handleErc20Transfer(
        log: JSONObject,
        challenge: CryptoPaymentChallenge,
        emitter: io.reactivex.FlowableEmitter<Web3Service.PaymentEvent>
    ) {
        val txHash = log.optString("transactionHash", "")
        val data = log.optString("data", "0x0")

        // Parse token amount from log data
        val rawAmount = BigInteger(data.removePrefix("0x"), 16)
        val decimals = challenge.tokenType.decimals
        val tokenAmount = BigDecimal(rawAmount).divide(BigDecimal.TEN.pow(decimals))
        val expectedAmount = BigDecimal(challenge.exactAmount)

        if (tokenAmount.compareTo(expectedAmount) == 0) {
            Timber.d("${challenge.tokenType.name} payment matched! tx=$txHash amount=$tokenAmount")
            emitter.onNext(Web3Service.PaymentEvent(
                challengeId = challenge.id,
                txHash = txHash,
                status = CryptoPaymentChallenge.PaymentStatus.CONFIRMING,
                confirmations = 0
            ))
            startConfirmationPolling(challenge.id, txHash, emitter)
        }
    }

    /**
     * Poll eth_getTransactionReceipt every 15s to track confirmations.
     */
    private fun startConfirmationPolling(
        challengeId: String,
        txHash: String,
        emitter: io.reactivex.FlowableEmitter<Web3Service.PaymentEvent>
    ) {
        val poller = Flowable.interval(CONFIRMATION_POLL_INTERVAL, TimeUnit.SECONDS, Schedulers.io())
            .subscribe { _ ->
                try {
                    val confirmations = getConfirmationCount(txHash)
                    if (confirmations >= 0) {
                        val status = if (confirmations >= CryptoPaymentChallenge.REQUIRED_CONFIRMATIONS) {
                            CryptoPaymentChallenge.PaymentStatus.CONFIRMED
                        } else {
                            CryptoPaymentChallenge.PaymentStatus.CONFIRMING
                        }

                        emitter.onNext(Web3Service.PaymentEvent(
                            challengeId = challengeId,
                            txHash = txHash,
                            status = status,
                            confirmations = confirmations
                        ))

                        if (status == CryptoPaymentChallenge.PaymentStatus.CONFIRMED) {
                            Timber.d("Payment confirmed for challenge $challengeId")
                            stopMonitoring(challengeId)
                            emitter.onComplete()
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error polling confirmations")
                }
            }

        activeMonitors[challengeId]?.let {
            activeMonitors[challengeId] = it.copy(confirmationPoller = poller)
        }
    }

    /**
     * Get confirmation count via JSON-RPC over HTTPS.
     */
    private fun getConfirmationCount(txHash: String): Int {
        val apiKey = cryptoRepository.getAlchemyApiKey()
        val url = "https://eth-mainnet.g.alchemy.com/v2/$apiKey"

        val requestBody = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "eth_getTransactionReceipt")
            put("params", JSONArray().apply { put(txHash) })
        }

        val httpRequest = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                requestBody.toString()
            ))
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()
        val body = response.body?.string() ?: return -1
        val json = JSONObject(body)
        val result = json.optJSONObject("result") ?: return -1

        val blockNumberHex = result.optString("blockNumber", "")
        if (blockNumberHex.isBlank()) return 0

        // Get current block number
        val blockRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "eth_blockNumber")
            put("params", JSONArray())
        }

        val blockHttpRequest = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                blockRequest.toString()
            ))
            .build()

        val blockResponse = okHttpClient.newCall(blockHttpRequest).execute()
        val blockBody = blockResponse.body?.string() ?: return -1
        val blockJson = JSONObject(blockBody)
        val currentBlockHex = blockJson.optString("result", "")

        if (currentBlockHex.isBlank()) return -1

        val txBlock = BigInteger(blockNumberHex.removePrefix("0x"), 16)
        val currentBlock = BigInteger(currentBlockHex.removePrefix("0x"), 16)

        return (currentBlock - txBlock).toInt().coerceAtLeast(0)
    }

    override fun stopMonitoring(challengeId: String) {
        activeMonitors.remove(challengeId)?.let { state ->
            state.webSocket?.close(1000, "Monitoring stopped")
            state.confirmationPoller?.dispose()
            Timber.d("Stopped monitoring for challenge $challengeId")
        }
    }

    override fun stopAll() {
        activeMonitors.keys.toList().forEach { stopMonitoring(it) }
    }

    override fun isMonitoring(challengeId: String): Boolean {
        return activeMonitors.containsKey(challengeId)
    }
}
