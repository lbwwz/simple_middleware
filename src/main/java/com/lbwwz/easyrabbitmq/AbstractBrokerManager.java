package com.lbwwz.easyrabbitmq;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.lbwwz.easyrabbitmq.core.Binding;
import com.lbwwz.easyrabbitmq.core.Exchange;
import com.lbwwz.easyrabbitmq.core.Queue;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 添加运行时对 Exchange 和Queue 的定义和绑定功能
 *
 * @author lbwwz
 */
public abstract class AbstractBrokerManager implements BrokerManager {

    private static final Logger logger = LoggerFactory.getLogger(AbstractBrokerManager.class);

    private static final String DELAYED_MESSAGE_EXCHANGE = "x-delayed-message";
    private static final String AMQP_PRE = "amq.";

    private static final String DEFAULT_EXCHANGE_NAME = "";
    protected ConnectionFactory connectionFactory;

    public AbstractBrokerManager(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;

    }

    public AbstractBrokerManager(String host, String userName, String password, String vHost) {
        if (this.connectionFactory == null) {
            this.connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(host);
            connectionFactory.setUsername(userName);
            connectionFactory.setPassword(password);
            connectionFactory.setVirtualHost(vHost);
            /**
             * 网络故障自动连接恢复
             */
            connectionFactory.setAutomaticRecoveryEnabled(true);
        }
    }


    @Override
    public void declareExchange(final Channel channel, final Exchange exchange) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("declaring exchange '" + exchange.getName() + "'");
        }
        if (!DEFAULT_EXCHANGE_NAME.equals(exchange.getName())) {
            try {
                if (exchange.isDelayed()) {
                    Map<String, Object> arguments = exchange.getArguments();
                    if (arguments == null) {
                        arguments = new HashMap<>();
                    } else {
                        arguments = new HashMap<>(arguments);
                    }
                    arguments.put("x-delayed-type", exchange.getType());
                    channel.exchangeDeclare(exchange.getName(), DELAYED_MESSAGE_EXCHANGE, exchange.isDurable(),
                        exchange.isAutoDelete(), exchange.isInternal(), arguments);
                } else {
                    channel.exchangeDeclare(exchange.getName(), exchange.getType(), exchange.isDurable(),
                        exchange.isAutoDelete(), exchange.isInternal(), exchange.getArguments());
                }
            } catch (IOException ignore) {
                if (logger.isDebugEnabled()) {
                    logger.error("Exception while declaring exchange: '" + exchange.getName() + "'", ignore);
                }
                //重复定义持久化Exchange
            }
        }
    }

    @Override
    public void declareQueue(final Channel channel, final Queue queue) throws IOException {

        if (logger.isDebugEnabled()) {
            logger.debug("declaring queue '" + queue.getName() + "'");
        }
        if (StringUtils.isNotBlank(queue.getName())) {
            if (logger.isDebugEnabled()) {
                logger.debug("declaring Queue '" + queue.getName() + "'");
            }
            try {
                channel.queueDeclare(queue.getName(), queue.isDurable(), queue.isExclusive(), queue
                        .isAutoDelete(),
                    queue.getArguments());
            } catch (IllegalArgumentException e) {
                if (logger.isDebugEnabled()) {
                    logger.error("Exception while declaring queue: '" + queue.getName() + "'");
                }
                //try {
                //    channel.close();
                //} catch (TimeoutException ignore) {
                //}
                //throw new IOException(e);
            }

        } else {
            // queueDeclare("", false, true, true, null);
        }
    }

    @Override
    public void declareBinding(final Channel channel, final Binding binding) throws IOException {

        if (logger.isDebugEnabled()) {
            logger.debug("Binding queue [" + binding.getQueue() + "] to exchange [" + binding.getExchange()
                + "] with routing key [" + binding.getRoutingKeys() + "]");
        }
        try {
            if (!DEFAULT_EXCHANGE_NAME.equals((binding.getExchange()))) {
                for (String routingKey : binding.getRoutingKeys()) {
                    channel.queueBind(binding.getQueue(), binding.getExchange(), routingKey,
                        binding.getArguments());
                }
            }
        } catch (IOException ignore) {
            //绑定的对象不存在或者绑定失败
        }
    }


}
