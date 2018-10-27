/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.socketio

import com.tinder.scarlet.Channel
import com.tinder.scarlet.Message
import com.tinder.scarlet.MessageQueue
import com.tinder.scarlet.Protocol
import com.tinder.scarlet.ProtocolEventAdapter
import com.tinder.scarlet.Topic
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

// TODO SocketIo server
class SocketIo(
    private val url: () -> String,
    private val options: IO.Options = IO.Options()
) : Protocol {

    private var mainChannel: SocketIoMainChannel? = null

    override fun createChannelFactory(): Channel.Factory {
        return object : Channel.Factory {
            override fun create(topic: Topic, listener: Channel.Listener): Channel {
                if (topic == Topic.Main) {
                    mainChannel = SocketIoMainChannel(
                        options,
                        listener
                    )
                    return mainChannel!!
                }
                return SocketIoMessageChannel(topic, listener)
            }
        }
    }

    override fun createOpenRequestFactory(channel: Channel): Protocol.OpenRequest.Factory {
        return object : Protocol.OpenRequest.Factory {
            override fun create(channel: Channel): Protocol.OpenRequest {
                if (channel.topic == Topic.Main) {
                    return SocketIo.MainChannelOpenRequest(url())
                }
                return SocketIo.MessageChannelOpenRequest(mainChannel?.socket)
            }
        }
    }

    override fun createEventAdapterFactory(): ProtocolEventAdapter.Factory {
        return object : ProtocolEventAdapter.Factory {}
    }

    data class MainChannelOpenRequest(val url: String) : Protocol.OpenRequest

    data class MessageChannelOpenRequest(val socket: Socket?) : Protocol.OpenRequest
}

class SocketIoMainChannel(
    private val options: IO.Options,
    private val listener: Channel.Listener
) : Channel {
    var socket: Socket? = null

    override fun open(openRequest: Protocol.OpenRequest) {
        val mainChannelOpenRequest = openRequest as SocketIo.MainChannelOpenRequest
        val socket = IO.socket(mainChannelOpenRequest.url, options)
        socket
            .on(Socket.EVENT_CONNECT) {
                listener.onOpened(this)
            }
            .on(Socket.EVENT_DISCONNECT) {
                listener.onClosed(this)
            }
            .on(Socket.EVENT_ERROR) {
                listener.onFailed(this, null)
            }
        socket.open()
        this.socket = socket
    }

    override fun close(closeRequest: Protocol.CloseRequest) {
        socket?.disconnect()
        socket = null
    }

    override fun forceClose() {
        socket?.disconnect()
        socket = null
    }

    override fun createMessageQueue(listener: MessageQueue.Listener): MessageQueue? {
        return null
    }
}

class SocketIoMessageChannel(
    override val topic: Topic,
    private val listener: Channel.Listener
) : Channel, MessageQueue {

    private var socket: Socket? = null
    private var messageQueueListener: MessageQueue.Listener? = null

    override fun open(openRequest: Protocol.OpenRequest) {
        val messageChannelOpenRequest = openRequest as SocketIo.MessageChannelOpenRequest
        socket = messageChannelOpenRequest.socket
        if (socket == null) {
            listener.onFailed(this, IllegalStateException("main topic is null"))
            return
        }
        socket?.on(topic.id) {
            val jsonObject = it[0] as JSONObject
            messageQueueListener?.onMessageReceived(this, this, Message.Text(jsonObject.toString()))
        }
        listener.onOpened(this)
    }

    override fun close(closeRequest: Protocol.CloseRequest) {
        socket?.off(topic.id)
        socket = null
        listener.onClosed(this)
    }

    override fun forceClose() {
        socket?.off(topic.id)
        socket = null
        listener.onClosed(this)
    }

    override fun createMessageQueue(listener: MessageQueue.Listener): MessageQueue {
        require(messageQueueListener == null)
        messageQueueListener = listener
        return this
    }

    override fun send(message: Message, messageMetaData: Protocol.MessageMetaData): Boolean {
        val socket = socket ?: return false
        when (message) {
            is Message.Text -> socket.emit(topic.id, message.value)
            is Message.Bytes -> {
                socket.emit(topic.id, message.value)
            }
        }
        return true
    }
}
