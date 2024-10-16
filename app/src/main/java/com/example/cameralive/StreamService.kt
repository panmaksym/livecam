package com.example.cameralive

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.webrtc.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StreamService : Service() {

    private val CHANNEL_ID = "StreamServiceChannel"
    private var isStreaming = false

    // Змінні для WebRTC
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var webSocket: WebSocket? = null
    private var isRegistered = false

    private val SIGNALING_SERVER_URL = "ws://109.201.241.40:5000"

    override fun onCreate() {
        super.onCreate()
        Log.i("StreamService", "Service created")
        createNotificationChannel()
        startForeground(1, createNotification())
        initializeWebRTC()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("StreamService", "Service started")
        if (!isStreaming) {
            isStreaming = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("StreamService", "Service destroyed")
        if (isStreaming) {
            releaseResources()
            isStreaming = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Stream Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Live")
            .setContentText("Streaming video in background")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun initializeWebRTC() {
        Log.i("StreamService", "Initializing WebRTC")
        initializePeerConnectionFactory()
        initializePeerConnection()
        initializeVideoCapturer()
        startCapture()
        connectToSignalingServer()
    }

    private fun initializePeerConnectionFactory() {
        Log.i("StreamService", "Initializing PeerConnectionFactory")
        if (peerConnectionFactory != null) {
            Log.i("StreamService", "PeerConnectionFactory already initialized")
            return
        }
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val eglBase = EglBase.create()
        val defaultVideoEncoderFactory =
            DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val defaultVideoDecoderFactory =
            DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
        Log.i("StreamService", "PeerConnectionFactory initialized")
    }

    private fun initializePeerConnection() {
        Log.i("StreamService", "Initializing PeerConnection")
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername("ac698ce8d56971a8082d6147")
                .setPassword("S1cN1tovf0LSFjM/")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
                .setUsername("ac698ce8d56971a8082d6147")
                .setPassword("S1cN1tovf0LSFjM/")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
                .setUsername("ac698ce8d56971a8082d6147")
                .setPassword("S1cN1tovf0LSFjM/")
                .createIceServer(),
            PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
                .setUsername("ac698ce8d56971a8082d6147")
                .setPassword("S1cN1tovf0LSFjM/")
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.i("StreamService", "New ICE candidate: $candidate")
                    if (isRegistered) {
                        sendIceCandidate(candidate)
                    }
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.i("StreamService", "ICE connection state changed: $newState")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.i("StreamService", "ICE connection receiving change: $receiving")
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    Log.i("StreamService", "Signaling state changed: $newState")
                }

                override fun onAddStream(stream: MediaStream) {
                    Log.i("StreamService", "onAddStream called (not used in Unified Plan)")
                }

                override fun onRemoveStream(p0: MediaStream?) {
                    Log.i("StreamService", "onRemoveStream (not used in Unified Plan)")
                }

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    Log.i("StreamService", "ICE gathering state changed: $newState")
                }

                override fun onTrack(transceiver: RtpTransceiver) {
                    Log.i("StreamService", "Track added: $transceiver")
                    // Оскільки ми не відображаємо відео, можна пропустити цю частину
                }

                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
                    Log.i("StreamService", "ICE candidates removed: ${candidates.joinToString()}")
                }

                override fun onDataChannel(channel: DataChannel) {
                    Log.i("StreamService", "Data channel created: $channel")
                }

                override fun onRenegotiationNeeded() {
                    Log.i("StreamService", "Renegotiation needed")
                }
            }) ?: throw IllegalStateException("Failed to create PeerConnection")
        Log.i("StreamService", "PeerConnection initialized")
    }

    private fun initializeVideoCapturer() {
        Log.i("StreamService", "Initializing video capturer")
        videoCapturer =
            createCameraCapturer(Camera1Enumerator(false)) ?: throw IllegalStateException("No camera found")
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // Пошук камери з тиловою орієнтацією
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    Log.i("StreamService", "Using back-facing camera: $deviceName")
                    return capturer
                }
            }
        }
        // Якщо не знайдено, спробувати будь-яку камеру
        for (deviceName in deviceNames) {
            val capturer = enumerator.createCapturer(deviceName, null)
            if (capturer != null) {
                Log.i("StreamService", "Using camera: $deviceName")
                return capturer
            }
        }
        return null
    }

    private fun startCapture() {
        Log.i("StreamService", "Starting video capture")
        val videoSource = peerConnectionFactory?.createVideoSource(false)
        val surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", EglBase.create().eglBaseContext)
        videoCapturer?.initialize(surfaceTextureHelper, this, videoSource?.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)
        val videoTrack = peerConnectionFactory?.createVideoTrack("videoTrack", videoSource)
        val streamId = "stream_id"
        peerConnection?.addTrack(videoTrack, listOf(streamId))
        Log.i("StreamService", "Video track added to PeerConnection")
    }

    private fun connectToSignalingServer() {
        Log.i("StreamService", "Connecting to signaling server: $SIGNALING_SERVER_URL")
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val request = Request.Builder().url(SIGNALING_SERVER_URL).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Log.i("WebSocket", "WebSocket connected")
                this@StreamService.webSocket = webSocket
                val json = JSONObject()
                json.put("type", "register")
                json.put("from", "Android")
                Log.i("WebSocket", "Sending registration message: $json")
                webSocket.send(json.toString())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.i("WebSocket", "WebSocket closed: $reason")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i("WebSocket", "Message received: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.getString("type")
                    Log.i("WebSocket", "Received message of type: $type")
                    when (type) {
                        "register-ack" -> {
                            isRegistered = true
                            Log.i("WebSocket", "Client successfully registered")
                            sendOffer() // Надсилаємо offer після успішної реєстрації
                        }
                        "answer" -> {
                            val sdp = json.getString("sdp")
                            Log.i("SDP", "Remote SDP Answer: $sdp")
                            if (peerConnection?.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                                val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                                peerConnection?.setRemoteDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        Log.i("WebRTC", "Remote SDP set successfully")
                                    }

                                    override fun onSetFailure(error: String) {
                                        Log.e("WebRTC", "Set SDP failed: $error")
                                    }

                                    override fun onCreateSuccess(sdp: SessionDescription) {}
                                    override fun onCreateFailure(error: String) {}
                                }, sessionDescription)
                            } else {
                                Log.w("WebRTC", "Skipping SDP answer set due to incorrect signaling state")
                            }
                        }
                        "candidate" -> {
                            if (isRegistered) {
                                val candidate = IceCandidate(
                                    json.getString("sdpMid"),
                                    json.getInt("sdpMLineIndex"),
                                    json.getString("candidate")
                                )
                                Log.i("WebSocket", "Received candidate: ${candidate.sdp}")
                                peerConnection?.addIceCandidate(candidate)
                            } else {
                                Log.w("WebSocket", "Received 'candidate' before registration")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Error parsing message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Log.e("WebSocket", "WebSocket connection failed: ${t.message}")
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    private fun sendOffer() {
        Log.i("StreamService", "Creating offer")
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.i("WebSocket", "Offer created: ${sdp.description}")
                if (sdp.description.isNotEmpty()) {
                    val localSdp = SessionDescription(SessionDescription.Type.OFFER, sdp.description)
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.i("WebRTC", "Local SDP set successfully")
                            sendSdpToServer(localSdp)
                        }

                        override fun onSetFailure(error: String) {
                            Log.e("WebRTC", "Set SDP failed: $error")
                        }

                        override fun onCreateSuccess(sdp: SessionDescription) {}
                        override fun onCreateFailure(error: String) {}
                    }, localSdp)
                } else {
                    Log.e("WebRTC", "SDP creation failed: SDP is empty")
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {
                Log.e("WebRTC", "Offer creation failed: $error")
            }

            override fun onSetFailure(error: String) {
                Log.e("WebRTC", "Set SDP failed: $error")
            }
        }, mediaConstraints)
    }

    private fun sendSdpToServer(sdp: SessionDescription) {
        val json = JSONObject()
        json.put("type", sdp.type.canonicalForm())
        json.put("sdp", sdp.description)
        json.put("from", "Android")
        json.put("target", "PC")
        Log.i("WebSocket", "Sending SDP to server: $json")
        if (webSocket != null) {
            webSocket?.send(json.toString())
        } else {
            Log.e("WebSocket", "WebSocket is not initialized")
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val json = JSONObject()
        json.put("type", "candidate")
        json.put("sdpMid", candidate.sdpMid)
        json.put("sdpMLineIndex", candidate.sdpMLineIndex)
        json.put("candidate", candidate.sdp)
        json.put("from", "Android")
        json.put("target", "PC")
        Log.i("WebSocket", "Sending ICE candidate to server: $json")
        if (webSocket != null) {
            webSocket?.send(json.toString())
        } else {
            Log.e("WebSocket", "WebSocket is not initialized")
        }
    }

    private fun releaseResources() {
        Log.i("StreamService", "Releasing resources")

        try {
            webSocket?.close(1000, "Stream stopped")
        } catch (e: Exception) {
            Log.e("StreamService", "Error closing WebSocket: ${e.message}")
        }
        webSocket = null

        try {
            peerConnection?.close()
        } catch (e: Exception) {
            Log.e("StreamService", "Error closing PeerConnection: ${e.message}")
        }
        peerConnection = null

        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e("StreamService", "Error stopping VideoCapturer: ${e.message}")
        }

        try {
            videoCapturer?.dispose()
        } catch (e: Exception) {
            Log.e("StreamService", "Error disposing VideoCapturer: ${e.message}")
        }
        videoCapturer = null

        try {
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.e("StreamService", "Error disposing PeerConnectionFactory: ${e.message}")
        }
        peerConnectionFactory = null

        isRegistered = false
    }
}
