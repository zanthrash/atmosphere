/*
 * Copyright 2013 Péter Miklós
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.container;

import org.atmosphere.container.version.Grizzly2WebSocket;
import org.atmosphere.container.version.JBossWebSocket;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.websocket.WebSocketProcessor;
import org.atmosphere.jboss.as.websockets.WebSocket;
import org.atmosphere.jboss.as.websockets.servlet.WebSocketServlet;
import org.atmosphere.jboss.websockets.Frame;
import org.atmosphere.jboss.websockets.frame.BinaryFrame;
import org.atmosphere.jboss.websockets.frame.CloseFrame;
import org.atmosphere.jboss.websockets.frame.TextFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Dispatches JBoss websocket events to Atmosphere's {@link org.atmosphere.websocket.WebSocketProcessor}.
 * This websocket handler is based Mike Brock's websockets implementation.
 * 
 * @author Péter Miklós
 * @see https://github.com/mikebrock/jboss-websockets
 */
public class JBossWebSocketHandler extends WebSocketServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(JBossWebSocketHandler.class);

    private static final String JBOSS_WEB_SOCKET_PROCESSOR = "jboss.webSocketProcessor";
    private final AtmosphereConfig config;
    private final WebSocketProcessor webSocketProcessor;

    public JBossWebSocketHandler(AtmosphereConfig config) {
        this.config = config;
        this.webSocketProcessor = WebSocketProcessorFactory.getDefault()
                .getWebSocketProcessor(config.framework());
    }

    @Override
    protected void onSocketOpened(WebSocket socket) throws IOException {
        logger.trace("WebSocket.onSocketOpened.");

        AtmosphereRequest r = AtmosphereRequest.wrap(socket.getServletRequest());
        org.atmosphere.websocket.WebSocket webSocket = new JBossWebSocket(socket, config);
        webSocketProcessor.open(webSocket, r, AtmosphereResponse.newInstance(config, r, webSocket));
    }

    @Override
    protected void onSocketClosed(WebSocket socket) throws IOException {
        logger.trace("WebSocket.onSocketClosed.");
        org.atmosphere.websocket.WebSocket webSocket = new JBossWebSocket(socket, config);
        webSocketProcessor.close(webSocket, 0);
    }

    @Override
    protected void onReceivedFrame(WebSocket socket) throws IOException {
        Frame frame = socket.readFrame();

        if (webSocketProcessor != null) {
            if (frame instanceof TextFrame) {
                logger.trace("WebSocket.onReceivedFrame (TextFrame)");
                webSocketProcessor.invokeWebSocketProtocol(new JBossWebSocket(socket, config), ((TextFrame) frame).getText());
            } else if (frame instanceof BinaryFrame) {
                logger.trace("WebSocket.onReceivedFrame (BinaryFrame)");
                BinaryFrame binaryFrame = (BinaryFrame) frame;
                webSocketProcessor.invokeWebSocketProtocol(new JBossWebSocket(socket, config), binaryFrame.getByteArray(), 0,
                        binaryFrame.getByteArray().length);
            } else if (frame instanceof CloseFrame) {
                // TODO shall we call this here?
                logger.trace("WebSocket.onReceivedFrame (CloseFrame)");
                webSocketProcessor.close(new JBossWebSocket(socket, config), 0);
            } else {
                logger.trace("WebSocket.onReceivedFrame skipping: " + frame);
            }
        } else {
            logger.trace("WebSocket.onReceivedFrame but no atmosphere processor in request, skipping: " + frame);
        }
    }
}