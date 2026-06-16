package osync.osync

import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.Stream
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.dsl.host
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multistream.StrictProtocolBinding
import io.libp2p.core.mux.StreamMuxer
import io.libp2p.core.mux.StreamMuxerProtocol
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.security.noise.NoiseXXSecureChannel
import io.libp2p.transport.tcp.TcpTransport
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

interface TunnelController {
    fun active()
}

class TunnelProtocolBinding(val localPort: Int) :
    StrictProtocolBinding<TunnelController>("/osync/tunnel/1.0.0", TunnelProtocol(localPort))

class TunnelProtocol(val localPort: Int) : ProtocolHandler<TunnelController>(Long.MAX_VALUE, Long.MAX_VALUE) {

    override fun initChannel(ch: io.libp2p.core.P2PChannel): CompletableFuture<TunnelController> {
        val stream = ch as Stream
        val controllerPromise = CompletableFuture<TunnelController>()

        if (!stream.isInitiator) {
            thread(name = "osync-host-tunnel-init", isDaemon = true) {
                var socket: Socket? = null
                try {
                    socket = Socket("127.0.0.1", localPort)
                    val socketInput = socket.getInputStream()
                    val socketOutput = socket.getOutputStream()

                    stream.pushHandler(object : SimpleChannelInboundHandler<ByteBuf>() {
                        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                            val bytes = ByteArray(msg.readableBytes())
                            msg.readBytes(bytes)
                            thread(name = "osync-host-netty-to-socket", isDaemon = true) {
                                try {
                                    socketOutput.write(bytes)
                                    socketOutput.flush()
                                } catch (e: Exception) {
                                    ctx.close()
                                    socket.close()
                                }
                            }
                        }

                        override fun channelInactive(ctx: ChannelHandlerContext) {
                            super.channelInactive(ctx)
                            socket.close()
                        }
                    })

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (socketInput.read(buffer).also { bytesRead = it } != -1) {
                        val byteBuf = Unpooled.copiedBuffer(buffer, 0, bytesRead)
                        stream.writeAndFlush(byteBuf)
                    }
                } catch (e: Exception) {
                    // Ignored
                } finally {
                    socket?.close()
                    stream.close()
                }
            }
        }

        controllerPromise.complete(object : TunnelController {
            override fun active() {}
        })
        return controllerPromise
    }
}

class P2PTunnelClient(
    val libp2pHost: Host,
    val targetMultiaddr: String
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = true

    fun start(): Int {
        val server = ServerSocket(0)
        serverSocket = server
        val localPort = server.localPort

        thread(name = "osync-client-tunnel-listener", isDaemon = true) {
            try {
                while (isRunning) {
                    val clientSocket = server.accept()
                    thread(name = "osync-client-tunnel-handler", isDaemon = true) {
                        handleClientConnection(clientSocket)
                    }
                }
            } catch (e: Exception) {
                // Socket closed
            }
        }

        return localPort
    }

    private fun handleClientConnection(socket: Socket) {
        var stream: Stream? = null
        try {
            val peerIdStr = targetMultiaddr.substringAfter("/p2p/")
            val peerId = PeerId.fromBase58(peerIdStr)
            val targetAddr = Multiaddr(targetMultiaddr)

            val streamPromise = libp2pHost.newStream<TunnelController>(listOf("/osync/tunnel/1.0.0"), peerId, targetAddr)
            stream = streamPromise.stream.get()

            val socketInput = socket.getInputStream()
            val socketOutput = socket.getOutputStream()

            stream.pushHandler(object : SimpleChannelInboundHandler<ByteBuf>() {
                override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                    val bytes = ByteArray(msg.readableBytes())
                    msg.readBytes(bytes)
                    thread(name = "osync-client-netty-to-socket", isDaemon = true) {
                        try {
                            socketOutput.write(bytes)
                            socketOutput.flush()
                        } catch (e: Exception) {
                            ctx.close()
                            socket.close()
                        }
                    }
                }

                override fun channelInactive(ctx: ChannelHandlerContext) {
                    super.channelInactive(ctx)
                    socket.close()
                }
            })

            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (socketInput.read(buffer).also { bytesRead = it } != -1) {
                val byteBuf = Unpooled.copiedBuffer(buffer, 0, bytesRead)
                stream.writeAndFlush(byteBuf)
            }
        } catch (e: Exception) {
            // Error
        } finally {
            socket.close()
            stream?.close()
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}

object P2PManager {
    var activeHost: Host? = null
    var activeTunnelClient: P2PTunnelClient? = null

    private fun getPublicIp(): String? {
        return try {
            val url = java.net.URL("https://api.ipify.org")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            null
        }
    }

    fun startHost(localPort: Int): String {
        stopAll()
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv6Addresses", "false")

        // Fetch public IP in the background
        var publicIp: String? = null
        val ipJob = thread(name = "osync-public-ip-fetch", isDaemon = true) {
            publicIp = getPublicIp()
        }
        try {
            ipJob.join(1500)
        } catch (e: Exception) {
            // Ignored
        }

        val host = host {
            identity {
                random()
            }
            transports {
                +::TcpTransport
            }
            secureChannels {
                + { k: PrivKey, m: List<StreamMuxer> -> NoiseXXSecureChannel(k, m) }
            }
            muxers {
                +StreamMuxerProtocol.Mplex
            }
            protocols {
                +TunnelProtocolBinding(localPort)
            }
            network {
                listen("/ip4/0.0.0.0/tcp/0")
            }
        }
        host.start().get()
        activeHost = host

        val listenAddrs = host.listenAddresses()
        val port = listenAddrs.firstOrNull()?.toString()
            ?.substringAfter("/tcp/")?.substringBefore("/p2p/")?.toIntOrNull() ?: 0
        val peerId = host.peerId.toString()

        val ips = mutableListOf<String>()
        publicIp?.let { ips.add(it) }
        ips.addAll(OsuUtils.getLocalSiteLocalAddresses())
        if (ips.isEmpty()) {
            ips.add(OsuUtils.getLocalIp())
        }

        val formattedAddrs = ips
            .distinct()
            .filter { it != "127.0.0.1" && it != "Ошибка сети" && it.isNotEmpty() }
            .map { "/ip4/$it/tcp/$port/p2p/$peerId" }

        return if (formattedAddrs.isNotEmpty()) {
            formattedAddrs.joinToString("\n")
        } else {
            listenAddrs.joinToString("\n") { it.toString() }
        }
    }

    fun startClientTunnel(targetMultiaddress: String): Int {
        stopAll()
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv6Addresses", "false")

        val host = host {
            identity {
                random()
            }
            transports {
                +::TcpTransport
            }
            secureChannels {
                + { k: PrivKey, m: List<StreamMuxer> -> NoiseXXSecureChannel(k, m) }
            }
            muxers {
                +StreamMuxerProtocol.Mplex
            }
            protocols {
                +TunnelProtocolBinding(8085)
            }
        }
        host.start().get()
        activeHost = host

        val tunnelClient = P2PTunnelClient(host, targetMultiaddress)
        activeTunnelClient = tunnelClient
        return tunnelClient.start()
    }

    fun stopAll() {
        activeTunnelClient?.stop()
        activeTunnelClient = null
        activeHost?.stop()?.get()
        activeHost = null
    }
}
