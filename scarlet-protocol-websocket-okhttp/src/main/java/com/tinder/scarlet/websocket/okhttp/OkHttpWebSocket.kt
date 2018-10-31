/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.websocket.okhttp

import com.tinder.scarlet.Channel
import com.tinder.scarlet.Protocol
import com.tinder.scarlet.ProtocolSpecificEventAdapter
import com.tinder.scarlet.utils.SimpleProtocolCloseRequestFactory
import com.tinder.scarlet.utils.SimpleProtocolOpenRequestFactory
import com.tinder.scarlet.websocket.ShutdownReason
import com.tinder.scarlet.websocket.WebSocketEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkHttpWebSocket(
    private val okHttpClient: OkHttpClient,
    private val requestFactory: RequestFactory
) : Protocol {

    override fun createChannelFactory(): Channel.Factory {
        return OkHttpWebSocketChannel.Factory(
            object : WebSocketFactory {
                override fun createWebSocket(request: Request, listener: WebSocketListener) {
                    okHttpClient.newWebSocket(request, listener)
                }
            }
        )
    }

    override fun createOpenRequestFactory(channel: Channel): Protocol.OpenRequest.Factory {
        return SimpleProtocolOpenRequestFactory {
            requestFactory.createOpenRequest()
        }
    }

    override fun createCloseRequestFactory(channel: Channel): Protocol.CloseRequest.Factory {
        return SimpleProtocolCloseRequestFactory {
            requestFactory.createCloseRequest()
        }
    }

    override fun createEventAdapterFactory(): ProtocolSpecificEventAdapter.Factory {
        return WebSocketEvent.Adapter.Factory()
    }

    data class OpenRequest(val okHttpRequest: Request) : Protocol.OpenRequest

    data class OpenResponse(val okHttpWebSocket: WebSocket, val okHttpResponse: Response) :
        Protocol.OpenResponse

    data class CloseRequest(val shutdownReason: ShutdownReason) : Protocol.CloseRequest

    data class CloseResponse(val shutdownReason: ShutdownReason) : Protocol.CloseResponse

    interface RequestFactory {
        fun createOpenRequest(): OpenRequest
        fun createCloseRequest(): CloseRequest
    }

    open class SimpleRequestFactory(
        private val createOpenRequestCallable: () -> Request,
        private val createCloseRequestCallable: () -> ShutdownReason
    ) : RequestFactory {
        override fun createOpenRequest(): OpenRequest {
            return OpenRequest(createOpenRequestCallable())
        }

        override fun createCloseRequest(): CloseRequest {
            return CloseRequest(createCloseRequestCallable())
        }
    }
}
