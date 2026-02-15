package com.callscreen.app.crypto

import com.callscreen.app.model.CryptoPaymentChallenge
import com.callscreen.app.repository.CryptoRepository
import com.callscreen.app.util.ScreenLog
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
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
        private const val TAG = "AlchemyWS"
        private const val ALCHEMY_WS_BASE = "wss://eth-mainnet.g.alchemy.com/v2/"
        private const val CONFIRMATION_POLL_INTERVAL = 15L // seconds
        private const val TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
        private const val MAX_RESPONSE_SIZE = 64 * 1024 // 64KB cap for JSON-RPC responses
        private const val JSON_MEDIA_TYPE = "application/json"
    }

    private val httpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
                ScreenLog.e(TAG, "FAILED: Alchemy API key is blank/not configured — cannot monitor payment for ${challenge.phoneNumber}")
                emitter.onError(IllegalStateException("Alchemy API key not configured"))
                return@create
            }

            ScreenLog.d(TAG, "Starting payment monitor for ${challenge.phoneNumber}: " +
                "amount=${challenge.exactAmount} ${challenge.tokenType.name} " +
                "wallet=${challenge.walletAddress} " +
                "challengeId=${challenge.id} " +
                "status=${challenge.status.name}")

            // FIRST: Check for already-mined transactions via HTTP
            // This catches payments made while the app was not monitoring
            try {
                val existingTx = checkExistingPayment(challenge, apiKey)
                if (existingTx != null) {
                    ScreenLog.d(TAG, "Found existing matching TX for ${challenge.phoneNumber}: " +
                        "hash=${existingTx.first}, confirmations=${existingTx.second}")

                    val status = if (existingTx.second >= CryptoPaymentChallenge.REQUIRED_CONFIRMATIONS) {
                        CryptoPaymentChallenge.PaymentStatus.CONFIRMED
                    } else {
                        CryptoPaymentChallenge.PaymentStatus.CONFIRMING
                    }

                    emitter.onNext(Web3Service.PaymentEvent(
                        challengeId = challenge.id,
                        txHash = existingTx.first,
                        status = status,
                        confirmations = existingTx.second
                    ))

                    if (status == CryptoPaymentChallenge.PaymentStatus.CONFIRMED) {
                        ScreenLog.d(TAG, "Existing TX already fully confirmed for ${challenge.phoneNumber}")
                        emitter.onComplete()
                        return@create
                    } else {
                        // Start polling confirmations for the existing TX
                        startConfirmationPolling(challenge.id, existingTx.first, emitter)
                    }
                } else {
                    ScreenLog.d(TAG, "No existing matching TX found for ${challenge.phoneNumber}, setting up WebSocket")
                }
            } catch (e: Exception) {
                ScreenLog.w(TAG, "HTTP check for existing payment failed (non-fatal): ${e.message}")
            }

            // THEN: Set up WebSocket for future transactions
            val wsUrl = "$ALCHEMY_WS_BASE$apiKey"
            val request = Request.Builder().url(wsUrl).build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    ScreenLog.d(TAG, "WebSocket CONNECTED for ${challenge.phoneNumber} (challenge ${challenge.id})")
                    subscribeToPayments(webSocket, challenge)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        handleMessage(text, challenge, emitter)
                    } catch (e: Exception) {
                        ScreenLog.w(TAG, "Error handling WebSocket message: ${e.message}")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    ScreenLog.e(TAG, "WebSocket FAILURE for ${challenge.phoneNumber}: ${t.message}", t)
                    if (!emitter.isCancelled) {
                        emitter.onError(t)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    ScreenLog.d(TAG, "WebSocket CLOSED for ${challenge.phoneNumber}: code=$code reason=$reason")
                    if (!emitter.isCancelled) {
                        emitter.onComplete()
                    }
                }
            }

            val webSocket = okHttpClient.newWebSocket(request, listener)
            activeMonitors[challenge.id] = MonitorState(challenge, webSocket)
            ScreenLog.d(TAG, "WebSocket connecting to Alchemy for ${challenge.phoneNumber}...")

            emitter.setCancellable {
                ScreenLog.d(TAG, "Monitor cancelled for ${challenge.phoneNumber}")
                stopMonitoring(challenge.id)
            }
        }, BackpressureStrategy.LATEST)
    }

    /**
     * Check for existing matching transactions via Alchemy HTTP API.
     * Uses alchemy_getAssetTransfers to find transfers to the wallet address
     * since the challenge was created.
     *
     * Returns Pair(txHash, confirmations) if found, null otherwise.
     */
    private fun checkExistingPayment(
        challenge: CryptoPaymentChallenge,
        apiKey: String
    ): Pair<String, Int>? {
        val url = "https://eth-mainnet.g.alchemy.com/v2/$apiKey"

        // First get current block number
        val blockNumBody = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "eth_blockNumber")
            put("params", JSONArray())
        }

        val blockNumRequest = Request.Builder()
            .url(url)
            .post(blockNumBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull()))
            .build()

        val blockNumJson = httpClient.newCall(blockNumRequest).execute().use { resp ->
            JSONObject(resp.body?.string()?.take(MAX_RESPONSE_SIZE) ?: return null)
        }
        val currentBlockHex = blockNumJson.optString("result", "") .ifBlank { return null }
        val currentBlock = BigInteger(currentBlockHex.removePrefix("0x"), 16)

        // Calculate from block: challenge age in blocks (12s per block) + 10 block buffer
        val challengeAgeSeconds = (System.currentTimeMillis() - challenge.createdAt) / 1000
        val blocksAgo = (challengeAgeSeconds / 12) + 20
        val fromBlock = (currentBlock - BigInteger.valueOf(blocksAgo)).coerceAtLeast(BigInteger.ZERO)
        val fromBlockHex = "0x${fromBlock.toString(16)}"

        ScreenLog.d(TAG, "Checking existing payments: fromBlock=$fromBlockHex (${blocksAgo} blocks ago) " +
            "toBlock=latest wallet=${challenge.walletAddress}")

        val category = when (challenge.tokenType) {
            CryptoPaymentChallenge.TokenType.ETH -> "external"
            else -> "erc20"
        }

        val transfersBody = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "alchemy_getAssetTransfers")
            put("params", JSONArray().apply {
                put(JSONObject().apply {
                    put("fromBlock", fromBlockHex)
                    put("toBlock", "latest")
                    put("toAddress", challenge.walletAddress)
                    put("category", JSONArray().apply { put(category) })
                    put("withMetadata", true)
                    put("maxCount", "0x14")
                })
            })
        }

        val transfersRequest = Request.Builder()
            .url(url)
            .post(transfersBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull()))
            .build()

        val transfersJson = httpClient.newCall(transfersRequest).execute().use { resp ->
            JSONObject(resp.body?.string()?.take(MAX_RESPONSE_SIZE) ?: return null)
        }

        val result = transfersJson.optJSONObject("result") ?: run {
            val error = transfersJson.optJSONObject("error")
            ScreenLog.w(TAG, "alchemy_getAssetTransfers error: ${error?.optString("message", "unknown")}")
            return null
        }
        val transfers = result.optJSONArray("transfers") ?: return null

        ScreenLog.d(TAG, "Found ${transfers.length()} transfers to wallet since challenge created")

        val expectedAmount = try {
            BigDecimal(challenge.exactAmount)
        } catch (e: NumberFormatException) {
            ScreenLog.e(TAG, "Invalid exactAmount '${challenge.exactAmount}': ${e.message}")
            return null
        }

        for (i in 0 until transfers.length()) {
            val transfer = transfers.getJSONObject(i)
            val valueRaw = transfer.optDouble("value", 0.0)
            val txHash = transfer.optString("hash", "")
            val transferValue = BigDecimal.valueOf(valueRaw)

            ScreenLog.d(TAG, "Transfer[$i]: hash=$txHash value=$transferValue expected=$expectedAmount")

            // Compare with tolerance for floating point
            val diff = transferValue.subtract(expectedAmount).abs()
            val tolerance = BigDecimal("0.0000000001") // 10^-10 tolerance for API rounding

            if (diff < tolerance) {
                ScreenLog.d(TAG, "MATCH FOUND: tx=$txHash value=$transferValue matches expected=$expectedAmount")

                // Get confirmation count for this TX
                val confirmations = getConfirmationCount(txHash)
                ScreenLog.d(TAG, "Matching TX $txHash has $confirmations confirmations")
                return Pair(txHash, confirmations.coerceAtLeast(0))
            }
        }

        ScreenLog.d(TAG, "No matching transfer found among ${transfers.length()} results")
        return null
    }

    private fun subscribeToPayments(webSocket: WebSocket, challenge: CryptoPaymentChallenge) {
        when (challenge.tokenType) {
            CryptoPaymentChallenge.TokenType.ETH -> subscribeEthTransactions(webSocket, challenge)
            CryptoPaymentChallenge.TokenType.USDC,
            CryptoPaymentChallenge.TokenType.USDT -> subscribeErc20Transfers(webSocket, challenge)
        }
    }

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
        ScreenLog.d(TAG, "Subscribed to pending ETH transactions for wallet ${challenge.walletAddress}")
    }

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
                        put(JSONObject.NULL)
                        put(recipientTopic)
                    })
                })
            })
        }
        webSocket.send(params.toString())
        ScreenLog.d(TAG, "Subscribed to ${challenge.tokenType.name} transfers for wallet ${challenge.walletAddress} " +
            "contract=$contractAddress")
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
            ScreenLog.d(TAG, "Subscription confirmed for ${challenge.phoneNumber}: subId=$subscriptionId")
            return
        }

        // Handle subscription events
        val params = json.optJSONObject("params") ?: run {
            // Check for errors
            if (json.has("error")) {
                val error = json.optJSONObject("error")
                ScreenLog.e(TAG, "Alchemy error: code=${error?.optInt("code")} msg=${error?.optString("message")}")
            }
            return
        }
        val result = params.optJSONObject("result") ?: run {
            ScreenLog.w(TAG, "WebSocket event with no result for ${challenge.phoneNumber}")
            return
        }

        ScreenLog.d(TAG, "WebSocket event received for ${challenge.phoneNumber} (${challenge.tokenType.name})")

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

        val hexStr = valueHex.removePrefix("0x").ifBlank { "0" }
        val weiValue = BigInteger(hexStr, 16)
        val ethValue = BigDecimal(weiValue).divide(BigDecimal.TEN.pow(18))
        val expectedAmount = try { BigDecimal(challenge.exactAmount) } catch (e: NumberFormatException) {
            ScreenLog.e(TAG, "Invalid exactAmount '${challenge.exactAmount}' for challenge ${challenge.id}")
            return
        }

        ScreenLog.d(TAG, "ETH TX received: hash=$txHash value=$ethValue expected=$expectedAmount " +
            "match=${ethValue.compareTo(expectedAmount) == 0}")

        if (ethValue.compareTo(expectedAmount) == 0) {
            ScreenLog.d(TAG, "ETH PAYMENT MATCHED for ${challenge.phoneNumber}! tx=$txHash amount=$ethValue")
            emitter.onNext(Web3Service.PaymentEvent(
                challengeId = challenge.id,
                txHash = txHash,
                status = CryptoPaymentChallenge.PaymentStatus.CONFIRMING,
                confirmations = 0
            ))
            startConfirmationPolling(challenge.id, txHash, emitter)
        } else {
            ScreenLog.d(TAG, "ETH TX amount mismatch: got $ethValue, need $expectedAmount (diff=${ethValue.subtract(expectedAmount)})")
        }
    }

    private fun handleErc20Transfer(
        log: JSONObject,
        challenge: CryptoPaymentChallenge,
        emitter: io.reactivex.FlowableEmitter<Web3Service.PaymentEvent>
    ) {
        val txHash = log.optString("transactionHash", "")
        val data = log.optString("data", "0x0")

        val hexStr = data.removePrefix("0x").ifBlank { "0" }
        val rawAmount = BigInteger(hexStr, 16)
        val decimals = challenge.tokenType.decimals
        val tokenAmount = BigDecimal(rawAmount).divide(BigDecimal.TEN.pow(decimals))
        val expectedAmount = try { BigDecimal(challenge.exactAmount) } catch (e: NumberFormatException) {
            ScreenLog.e(TAG, "Invalid exactAmount '${challenge.exactAmount}' for challenge ${challenge.id}")
            return
        }

        ScreenLog.d(TAG, "${challenge.tokenType.name} transfer: hash=$txHash amount=$tokenAmount expected=$expectedAmount " +
            "match=${tokenAmount.compareTo(expectedAmount) == 0}")

        if (tokenAmount.compareTo(expectedAmount) == 0) {
            ScreenLog.d(TAG, "${challenge.tokenType.name} PAYMENT MATCHED for ${challenge.phoneNumber}! tx=$txHash")
            emitter.onNext(Web3Service.PaymentEvent(
                challengeId = challenge.id,
                txHash = txHash,
                status = CryptoPaymentChallenge.PaymentStatus.CONFIRMING,
                confirmations = 0
            ))
            startConfirmationPolling(challenge.id, txHash, emitter)
        }
    }

    private fun startConfirmationPolling(
        challengeId: String,
        txHash: String,
        emitter: io.reactivex.FlowableEmitter<Web3Service.PaymentEvent>
    ) {
        ScreenLog.d(TAG, "Starting confirmation polling for $txHash (every ${CONFIRMATION_POLL_INTERVAL}s)")

        val poller = Flowable.interval(CONFIRMATION_POLL_INTERVAL, TimeUnit.SECONDS, Schedulers.io())
            .subscribe { tick ->
                try {
                    val confirmations = getConfirmationCount(txHash)
                    ScreenLog.d(TAG, "Poll #$tick for $txHash: $confirmations/${CryptoPaymentChallenge.REQUIRED_CONFIRMATIONS} confirmations")

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
                            ScreenLog.d(TAG, "Payment FULLY CONFIRMED for challenge $challengeId ($confirmations blocks)")
                            stopMonitoring(challengeId)
                            emitter.onComplete()
                        }
                    } else {
                        ScreenLog.w(TAG, "Poll #$tick: getConfirmationCount returned -1 (TX not yet mined?)")
                    }
                } catch (e: Exception) {
                    ScreenLog.w(TAG, "Error polling confirmations for $txHash: ${e.message}")
                }
            }

        activeMonitors[challengeId]?.let {
            activeMonitors[challengeId] = it.copy(confirmationPoller = poller)
        }
    }

    /**
     * Get confirmation count via JSON-RPC batch request over HTTPS.
     * Uses batch to fetch both eth_getTransactionReceipt and eth_blockNumber
     * in a single round-trip.
     */
    private fun getConfirmationCount(txHash: String): Int {
        val apiKey = cryptoRepository.getAlchemyApiKey()
        val url = "https://eth-mainnet.g.alchemy.com/v2/$apiKey"

        val batchBody = JSONArray().apply {
            put(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "eth_getTransactionReceipt")
                put("params", JSONArray().apply { put(txHash) })
            })
            put(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", "eth_blockNumber")
                put("params", JSONArray())
            })
        }

        val httpRequest = Request.Builder()
            .url(url)
            .post(batchBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull()))
            .build()

        val batchResponse = httpClient.newCall(httpRequest).execute().use { resp ->
            JSONArray(resp.body?.string()?.take(MAX_RESPONSE_SIZE) ?: return -1)
        }

        var blockNumberHex = ""
        var currentBlockHex = ""
        for (i in 0 until batchResponse.length()) {
            val item = batchResponse.getJSONObject(i)
            when (item.optInt("id")) {
                1 -> {
                    val result = item.optJSONObject("result") ?: return -1
                    blockNumberHex = result.optString("blockNumber", "")
                }
                2 -> {
                    currentBlockHex = item.optString("result", "")
                }
            }
        }

        if (blockNumberHex.isBlank()) return 0
        if (currentBlockHex.isBlank()) return -1

        val txBlock = try { BigInteger(blockNumberHex.removePrefix("0x").ifBlank { "0" }, 16) } catch (e: NumberFormatException) { return -1 }
        val currentBlock = try { BigInteger(currentBlockHex.removePrefix("0x").ifBlank { "0" }, 16) } catch (e: NumberFormatException) { return -1 }

        return (currentBlock - txBlock).toInt().coerceAtLeast(0)
    }

    override fun stopMonitoring(challengeId: String) {
        activeMonitors.remove(challengeId)?.let { state ->
            state.webSocket?.close(1000, "Monitoring stopped")
            state.confirmationPoller?.dispose()
            ScreenLog.d(TAG, "Stopped monitoring for challenge $challengeId (phone=${state.challenge.phoneNumber})")
        }
    }

    override fun stopAll() {
        ScreenLog.d(TAG, "Stopping all monitors (${activeMonitors.size} active)")
        activeMonitors.keys.toList().forEach { stopMonitoring(it) }
    }

    override fun isMonitoring(challengeId: String): Boolean {
        return activeMonitors.containsKey(challengeId)
    }
}
