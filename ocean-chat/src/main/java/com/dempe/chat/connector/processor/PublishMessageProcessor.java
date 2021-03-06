package com.dempe.chat.connector.processor;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dempe.chat.common.mqtt.messages.AbstractMessage;
import com.dempe.chat.common.mqtt.messages.PublishMessage;
import com.dempe.chat.connector.NettyUtils;
import com.dempe.chat.connector.store.ClientSession;
import com.dempe.logic.api.UserGroupService;
import com.dempe.ocean.common.TopicType;
import io.netty.channel.Channel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 扩展publish消息，规定topicName为空的时候为单播请求，即问答模式
 * publish消息里层协议封装
 * 1.有新的消息后server主动push
 * 2.client收到push消息后，发sync同步消息，
 * 3.server按redis sort set里面版本好发送消息给客户端
 * 4.client返回收到的序列号
 * User: Dempe
 * Date: 2016/4/11
 * Time: 20:48
 * To change this template use File | Settings | File Templates.
 */
public class PublishMessageProcessor extends MessageProcessor {


    @Autowired
    private UserGroupService userGroupService;

    /**
     * 1.存储消息到mongodb
     * 2.
     *
     * @param session
     * @param msg
     */
    public void processPublish(final Channel session, final PublishMessage msg) throws Exception {
        LOGGER.trace("PUB --PUBLISH--> SRV executePublish invoked with {}", msg);
        String clientID = NettyUtils.clientID(session);
        final String topic = msg.getTopicName();
        //check if the topic can be wrote
        final AbstractMessage.QOSType qos = msg.getQos();
        final Integer messageID = msg.getMessageID();
        LOGGER.info("PUBLISH from clientID <{}> on topic <{}> with QoS {}", clientID, topic, qos);

        if (StringUtils.isBlank(topic)) {
            // 规定如果是null topic，则为内置协议

        } else if (StringUtils.startsWith(topic, TopicType.FRIEND.getType())) {
            // 发给朋友的消息
            handleFriendMsg(topic, msg);
        } else if (StringUtils.startsWith(topic, TopicType.GROUP.getType())) {
            // 发给群组的消息
            handleGroupMsg(topic, msg);

        } else if (StringUtils.startsWith(topic, TopicType.MYSELF.getType())) {
            // 发给自己的，属于传统的问答模式的消息，这类消息需要直接透传到逻辑层，交由逻辑层处理
            handleMyselfMsg(topic, session, msg);
        }

    }

    private void handleFriendMsg(String topic, final PublishMessage msg) {
        LOGGER.info("handleFriendMsg topic:{},msg:{}", topic, msg);
        String[] split = topic.split("\\|");
        if (split.length == 2) {
            String toUid = split[1];
            ClientSession clientSession = m_sessionsStore.sessionForClient(toUid);
            directSend(clientSession, topic, AbstractMessage.QOSType.QOSType, msg.getPayload(), false,
                    (int) clientSession.getNextMessageId());
        }
    }


    private void handleGroupMsg(String topic, final PublishMessage msg) {
        String[] split = topic.split("\\|");
        if (split.length != 2) {
            LOGGER.warn("wrong topic for request & response msg");
            return;
        }
        String groupId = split[1];
        JSONObject jsonObject = userGroupService.listUidByGroupId(groupId);
        JSONArray data = jsonObject.getJSONArray("data");
        for (int i = 0; i < data.size(); i++) {
            String uid = data.getString(i);
            ClientSession clientSession = m_sessionsStore.sessionForClient(uid);
            directSend(clientSession, topic, AbstractMessage.QOSType.QOSType, msg.getPayload(), false,
                    (int) clientSession.getNextMessageId());

        }
    }

    /**
     * 处理问答类型的消息，消息透传到logic层，logic层返回数据后直接封装到mqtt publish msg的payload中返回给客户端
     * 这类消息Qos为0，不保证消息一定到达
     *
     * @param topic
     * @param session
     * @param msg
     * @throws Exception
     */
    private void handleMyselfMsg(String topic, final Channel session, final PublishMessage msg) throws Exception {

    }

}
