/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.carbondata.core.dictionary.server;

import java.net.InetSocketAddress;

import org.apache.carbondata.common.logging.LogService;
import org.apache.carbondata.common.logging.LogServiceFactory;
import org.apache.carbondata.core.dictionary.generator.key.DictionaryKey;
import org.apache.carbondata.core.dictionary.generator.key.DictionaryKeyType;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;


/**
 * Dictionary Server to generate dictionary keys.
 */
public class DictionaryServer {

  private static final LogService LOGGER =
          LogServiceFactory.getLogService(DictionaryServer.class.getName());

  private DictionaryServerHandler dictionaryServerHandler;

  private EventLoopGroup boss;
  private EventLoopGroup worker;

  /**
   * start dictionary server
   *
   * @param port
   * @throws Exception
   */
  public void startServer(int port) {
    dictionaryServerHandler = new DictionaryServerHandler();
    boss = new NioEventLoopGroup();
    worker = new NioEventLoopGroup();
    // Configure the server.
    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(boss, worker);
      bootstrap.channel(NioServerSocketChannel.class);

      bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
        @Override public void initChannel(SocketChannel ch) throws Exception {
          ChannelPipeline pipeline = ch.pipeline();
          pipeline.addLast("DictionaryServerHandler", dictionaryServerHandler);
        }
      });
      bootstrap.bind(port).sync();

      LOGGER.audit("Server Start!");
    } catch (Exception e) {
      LOGGER.error(e, "Dictionary Server Start Failed");
      throw new RuntimeException(e);
    }
  }

  /**
   * shutdown dictionary server
   *
   * @throws Exception
   */
  public void shutdown() throws Exception {
    DictionaryKey key = new DictionaryKey();
    key.setType(DictionaryKeyType.WRITE_DICTIONARY);
    dictionaryServerHandler.processMessage(key);
    worker.shutdownGracefully();
    boss.shutdownGracefully();
    // Wait until all threads are terminated.
    boss.terminationFuture().sync();
    worker.terminationFuture().sync();
  }
}