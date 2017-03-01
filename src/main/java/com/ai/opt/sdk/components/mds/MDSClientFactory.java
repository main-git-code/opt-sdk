package com.ai.opt.sdk.components.mds;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ai.opt.sdk.components.base.ComponentConfigLoader;
import com.ai.opt.sdk.components.mds.constants.MDSConsumerConstants;
import com.ai.opt.sdk.components.mds.constants.MDSSenderConstants;
import com.ai.opt.sdk.components.mo.PaasConf;
import com.ai.opt.sdk.components.util.ConfigTool;
import com.ai.opt.sdk.constants.SDKConstants;
import com.ai.opt.sdk.exception.SDKException;
import com.ai.paas.ipaas.mds.IMessageConsumer;
import com.ai.paas.ipaas.mds.IMessageSender;
import com.ai.paas.ipaas.mds.IMsgProcessorHandler;
import com.ai.paas.ipaas.mds.MsgConsumerCmpFactory;
import com.ai.paas.ipaas.mds.MsgConsumerFactory;
import com.ai.paas.ipaas.mds.MsgSenderCmpFactory;
import com.ai.paas.ipaas.mds.MsgSenderFactory;
import com.ai.paas.ipaas.uac.vo.AuthDescriptor;
import com.ai.paas.ipaas.util.StringUtil;
import com.alibaba.fastjson.JSON;

/**
 * MDS客户端工厂
 * Date: 2017年2月10日 <br>
 * Copyright (c) 2017 asiainfo.com <br>
 * 
 * @author
 */
public final class MDSClientFactory {
	private static final Logger LOG = LoggerFactory.getLogger(MDSClientFactory.class);
	private static Map<String, IMessageSender> sendMap_serviceMode = new ConcurrentHashMap<String, IMessageSender>();
	private static Map<String, IMessageConsumer> recvMap_serviceMode = new ConcurrentHashMap<String, IMessageConsumer>();
	private static Map<String, IMessageSender> sendMap_sdkMode = new ConcurrentHashMap<String, IMessageSender>();
	private static Map<String, IMessageConsumer> recvMap_sdkMode = new ConcurrentHashMap<String, IMessageConsumer>();

    private MDSClientFactory() {

    }

    /**
     * 获取发送者客户端
     * @param mdsns 消息队列命名空间
     * @return 发送者客户端信息
     * @author
     */
    public static IMessageSender getSenderClient(String mdsns) {
    	PaasConf authInfo = ComponentConfigLoader.getInstance().getPaasAuthInfo();
    	if(StringUtil.isBlank(authInfo.getPaasSdkMode())||SDKConstants.PAASMODE.PAAS_SERVICE_MODE.equals(authInfo.getPaasSdkMode())){
    		return getSenderClientByServiceMode(mdsns);
    	}
    	else{
    		return getSenderClientBySdkMode(mdsns);
    	}
    }
    /**
     * 获取消费者客户端
     * @param mdsns 消息队列命名空间
     * @param msgProcessorHandler 消费者客户端信息
     * @return
     * @author
     */
    public static IMessageConsumer getConsumerClient(String mdsns, IMsgProcessorHandler msgProcessorHandler){
    		return getConsumerClient(mdsns, msgProcessorHandler,null);
    }
    /**
     * 获取消费者客户端
     * @param mdsns
     * @param msgProcessorHandler 消费者客户端信息
     * @param consumerId
     * @return
     * @author
     */
    public static IMessageConsumer getConsumerClient(String mdsns, IMsgProcessorHandler msgProcessorHandler,String consumerId){
		PaasConf authInfo = ComponentConfigLoader.getInstance().getPaasAuthInfo();
		if(StringUtil.isBlank(authInfo.getPaasSdkMode())||SDKConstants.PAASMODE.PAAS_SERVICE_MODE.equals(authInfo.getPaasSdkMode())){
			return getConsumerClientByServiceMode(mdsns, msgProcessorHandler,consumerId);
		}
		else{
			return getConsumerClientBySdkMode(mdsns, msgProcessorHandler,consumerId);
		}
    		
    }
    
    /**
     * 由SDK模式获取客户端
     * @param mdsns
     * @param msgProcessorHandler
     * @param consumerId
     * @return
     * @author
     */
    private static IMessageConsumer getConsumerClientBySdkMode(String mdsns, IMsgProcessorHandler msgProcessorHandler,
			String consumerId) {
    	if(StringUtil.isBlank(consumerId)){
    		consumerId="consumer";
		}
    	if (StringUtil.isBlank(mdsns)) {
			throw new SDKException("请输入消息服务配置映射的常量标识");
		}
		String mdsId = ConfigTool.getMDSId(mdsns);
        PaasConf authInfo = ComponentConfigLoader.getInstance().getPaasAuthInfo();
        String appname = authInfo.getCcsAppName();
		LOG.info("authInfo="+JSON.toJSONString(authInfo));
		Properties kafkaConsumerProp=ConfigTool.assembleMdsConsumerProperties(mdsns);
		String topicId=kafkaConsumerProp.getProperty(MDSConsumerConstants.MDS_TOPIC);
        String keyId=appname+"."+mdsId+"."+consumerId;
		
		IMessageConsumer client;
		try {
			if (!recvMap_sdkMode.containsKey(keyId)) {
				kafkaConsumerProp.put(MDSConsumerConstants.KAFKA_CONSUMER_ID, consumerId);
				
				String mdsConsumerBasePath=kafkaConsumerProp.getProperty(MDSConsumerConstants.MDS_CONSUMER_BASE_PATH);
				String newMdsConsumerBasePath=mdsConsumerBasePath+"/"+consumerId;
				
				kafkaConsumerProp.put(MDSConsumerConstants.KAFKA_CONSUMER_ID, consumerId);
				kafkaConsumerProp.put(MDSConsumerConstants.MDS_PARTITION_RUNNINGLOCK_PATH, newMdsConsumerBasePath+ "/partitions/running");
				kafkaConsumerProp.put(MDSConsumerConstants.MDS_PARTITION_PAUSELOCK_PATH, newMdsConsumerBasePath+ "/partitions/pause");
				kafkaConsumerProp.put(MDSConsumerConstants.MDS_PARTITION_OFFSET_BASEPATH, newMdsConsumerBasePath+ "/offsets");
				
				client = MsgConsumerCmpFactory.getClient(kafkaConsumerProp,topicId, msgProcessorHandler);
				recvMap_sdkMode.put(keyId, client);
			}
			else{
				client=recvMap_sdkMode.get(keyId);
			}
		} catch (Exception e) {
			throw new SDKException("无法获取消息服务[" + mdsId + "]对应的客户端实例", e);
		}
		return client;
	}

    /**
     * 由服务模式获取客户端
     * @param mdsns
     * @param msgProcessorHandler
     * @param consumerId
     * @return
     * @author
     */
	private static IMessageConsumer getConsumerClientByServiceMode(String mdsns,
			IMsgProcessorHandler msgProcessorHandler, String consumerId) {
		if (StringUtil.isBlank(mdsns)) {
            throw new SDKException("请输入消息服务配置映射的常量标识");
        }
        String mdsId = ConfigTool.getMDSId(mdsns);
        String mdsPwd = ConfigTool.getServicePwd(mdsId);
        PaasConf authInfo = ComponentConfigLoader.getInstance().getPaasAuthInfo();
        AuthDescriptor authDescriptor = new AuthDescriptor(authInfo.getAuthUrl(),
                authInfo.getPid(), mdsPwd, mdsId);
        String keyId=authInfo.getPid()+"."+mdsId;
        if(!StringUtil.isBlank(consumerId)){
        	keyId+="."+consumerId;
        }
        else{
        	keyId+="."+"consumer";
        }
        IMessageConsumer client;
        try {
        	if (!recvMap_serviceMode.containsKey(keyId)) {
        		if(!StringUtil.isBlank(consumerId)){
        			client = MsgConsumerFactory.getClient(authDescriptor, msgProcessorHandler,consumerId);
        		}
        		else{
        			client = MsgConsumerFactory.getClient(authDescriptor, msgProcessorHandler);
        		}
        		recvMap_serviceMode.put(keyId, client);
    		}
        	else{
        		client=recvMap_serviceMode.get(keyId);
        	}
        } catch (Exception e) {
            throw new SDKException("无法获取消息服务[" + mdsId + "]对应的客户端实例", e);
        }
        return client;
	}

	/**
	 * 由服务模式获取发送者客户端
	 * @param mdsns
	 * @return
	 * @author
	 */
	private static IMessageSender getSenderClientByServiceMode(String mdsns) {
		if (StringUtil.isBlank(mdsns)) {
            throw new SDKException("请输入消息服务配置映射的常量标识");
        }
        String mdsId = ConfigTool.getMDSId(mdsns);
        String mdsPwd = ConfigTool.getServicePwd(mdsId);
        PaasConf authInfo = ComponentConfigLoader.getInstance().getPaasAuthInfo();
        AuthDescriptor authDescriptor = new AuthDescriptor(authInfo.getAuthUrl(),
                authInfo.getPid(), mdsPwd, mdsId);
        String keyId=authInfo.getPid()+"."+mdsId;
        IMessageSender client;
        try {
        	if (!sendMap_serviceMode.containsKey(keyId)) {
        		client = MsgSenderFactory.getClient(authDescriptor);
    			sendMap_serviceMode.put(keyId, client);
    		}
        	else{
        		client=sendMap_serviceMode.get(keyId);
        	}
        } catch (Exception e) {
            throw new SDKException("无法获取消息服务[" + mdsId + "]对应的客户端实例", e);
        }
        return client;
	}
    
	/**
	 * 由SDK模式获取发送者客户端
	 * @param mdsns
	 * @return
	 * @author
	 */
	private static IMessageSender getSenderClientBySdkMode(String mdsns) {
		if (StringUtil.isBlank(mdsns)) {
            throw new SDKException("请输入消息服务配置映射的常量标识");
        }
        String mdsId = ConfigTool.getMDSId(mdsns);
        PaasConf authInfo = ComponentConfigLoader.getInstance().getPaasAuthInfo();
        String appname = authInfo.getCcsAppName();
		LOG.info("authInfo="+JSON.toJSONString(authInfo));
		Properties kafkaSenderProp=ConfigTool.assembleMdsSenderProperties(mdsns);
		String topicId=kafkaSenderProp.getProperty(MDSSenderConstants.MDS_TOPIC);
        String keyId=appname+"."+mdsId;
        IMessageSender client;
        try {
        	if (!sendMap_sdkMode.containsKey(keyId)) {
        		client = MsgSenderCmpFactory.getClient(kafkaSenderProp,topicId);
        		sendMap_sdkMode.put(keyId, client);
    		}
        	else{
        		client=sendMap_sdkMode.get(keyId);
        	}
        } catch (Exception e) {
            throw new SDKException("无法获取消息服务[" + mdsId + "]对应的客户端实例", e);
        }
        return client;
	}

}
