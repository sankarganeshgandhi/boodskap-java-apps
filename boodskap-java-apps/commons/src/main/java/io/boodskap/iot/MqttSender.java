/*******************************************************************************
 * Copyright (C) 2017 Boodskap Inc
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package io.boodskap.iot;

import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT Sender implementation
 * Publish messages to Boodskap IoT Platform through MQTT
 * 
 * @author Jegan Vincent
 *
 * @see HttpSender
 * @see UDPSender
 */
public class MqttSender extends AbstractPublisher implements IMqttMessageListener {

	private static final Logger LOG = LoggerFactory.getLogger(MqttSender.class);

	protected final String mqttUrl;
	private final String clientId;
	private final String userName;
	private final char[] password;
	
	private MqttClient mqtt;
	private MqttConnectOptions opts;
	
	/**
	 * @param mqttUrl <code> Ex: tcp://mqtt.boodskap.io</code>
	 * @param heartbeat <code>heartbeat in milliseconds</code>
	 * @param domainKey <code> You will get it from Boodskap Dashboard</code>
	 * @param apiKey <code> You will get it from Boodskap Dashboard</code>
	 * @param deviceId  <code> Your device id, ex: MyCamera, MyKitchenCamara, etc...</code>
	 * @param deviceModel <code> Ex: RaspCAM, ArduCAM, etc...</code>
	 * @param firmwareVersion <code> Ex: 1.0.0, 0.0.7, etc...</code>
	 */
	public MqttSender(String mqttUrl, long heartbeat, String domainKey, String apiKey, String deviceId, String deviceModel, String firmwareVersion, MessageHandler handler) {
		super(domainKey, apiKey, deviceId, deviceModel, firmwareVersion, heartbeat, handler);
		this.mqttUrl = mqttUrl;
		clientId = String.format("DEV_%s", deviceId);
		userName = String.format("DEV_%s", domainKey);
		password = apiKey.toCharArray();
		
	}
	
	/**
	 * Open connection with MQTT server channel with auto reconnect on
	 * @throws Exception 
	 */
	public void doOpen() throws Exception {
		open(true);
	}

	/**
	 * 
	 * Open connection with Boodskap MQTT Server
	 * @param reconnect <pre>true to reconnect automatically on connection failures</pre>
	 * @throws Exception 
	 */
	public void open(boolean reconnect) throws Exception {
		//TODO make all these configurable
		opts = new MqttConnectOptions();
		opts.setKeepAliveInterval(30);
		opts.setCleanSession(true);
		opts.setUserName(userName);
		opts.setPassword(password);
		opts.setAutomaticReconnect(reconnect);
		
		mqtt = new MqttClient(mqttUrl, clientId, new MemoryPersistence());
		mqtt.setManualAcks(false);
		mqtt.setTimeToWait(3000);
		mqtt.connect(opts);
		mqtt.subscribe(getDeviceTopic(), this);
		
	}
	
	/**
	 * Check if you are connected
	 * @return <code>true if connected else false</code>
	 */
	public boolean isConnected() {
		return (null != mqtt && mqtt.isConnected());
	}
	
	/**
	 * Closes the connection
	 * @throws MqttException
	 */
	protected void doClose() throws MqttException {
		
		if(null != mqtt && mqtt.isConnected()) {
			try{mqtt.disconnect(3000);}catch(Exception ex) {LOG.warn("DISCONNECT", ex);}
			try{if(mqtt.isConnected()) mqtt.disconnectForcibly();}catch(Exception ex) {LOG.warn("DISCONNECT-FORCED", ex);}
			mqtt.close();
			mqtt = null;
		}
	}
	
	/**
	 * Send a well formatted Boodskap message
	 * @param messageId <pre>Message ID defined in Boodskap Platform</pre>
	 * @param json <pre>A Map containing message fields</pre>
	 * @throws Exception
	 */
	protected void doPublish(int messageId, Map<String, Object> json) throws MqttPersistenceException, JSONException, MqttException {
		sendMessage(messageId, json, 0, false);
	}
	
	/**
	 * Send a well formatted Boodskap message
	 * @param messageId <pre>Message ID defined in Boodskap Platform</pre>
	 * @param json <pre>A Map containing message fields</pre>
	 * @param qos <pre>MQTT QOS, 0|1|2, At most once (0), At least once (1), Exactly once (2)</pre>
	 * @param retained <pre>true to persist in MQTT server</pre>
	 * @throws JSONException
	 * @throws MqttPersistenceException
	 * @throws MqttException
	 */
	public void sendMessage(int messageId, Map<String, Object> json, int qos, boolean retained) throws JSONException, MqttPersistenceException, MqttException {
		switch(messageId) {
		case MSG_PING:
			LOG.info("Sending ping");
			break;
		case MSG_ACK:
			LOG.info("Sending ack {}", json);
			break;
		default:
			LOG.info("Sending message id:{} {}", messageId, json);
			break;
		}
		JSONObject data = new JSONObject(json);
		byte[] payload = data.toString().getBytes();
		mqtt.publish(getTopic(messageId), payload, qos, retained);
	}
	
	/**
	 * Send a image captured from a Camera or upload a picture
	 * @param cameraId <pre>Ex: 0, 1, 2, ...)</pre>
	 * @param live <pre>true to live broadcast, false to store in the server for later display</pre>
	 * @param format <pre>Ex: png|jpg|bmp...</pre>
	 * @param data <pre>Binary data of the image</pre>
	 * @param qos <pre>MQTT QOS, 0|1|2, At most once (0), At least once (1), Exactly once (2)</pre>
	 * @param retained <pre>true to persist in MQTT server</pre>
	 * @throws MqttPersistenceException
	 * @throws MqttException
	 */
	public void sendPicture(String cameraId, boolean live, String format, byte[] data, int qos, boolean retained) throws MqttPersistenceException, MqttException {
		if(cameraId.indexOf("/") != -1) {
			cameraId = cameraId.replaceAll("/", "_");
		}
		final String topic = getSnapTopic(cameraId, live, format);
		LOG.info("sending image size: {}, topic:{}", data.length, topic);
		mqtt.publish(topic, data, qos, retained);
	}

	/**
	 * 
	 * Send a video captured from a Camera or upload it from local storage
	 * @param cameraId <pre>Ex: 0, 1, 2, ...)</pre>
	 * @param live <pre>true to live broadcast, false to store in the server for later display</pre>
	 * @param format <pre>Ex: h24|mp4|...</pre>
	 * @param data <pre>Binary data of the image</pre>
	 * @param qos <pre>MQTT QOS, 0|1|2, At most once (0), At least once (1), Exactly once (2)</pre>
	 * @param retained <pre>true to persist in MQTT server</pre>
	 * @throws MqttPersistenceException
	 * @throws MqttException
	 */
	public void sendVideo(String cameraId, boolean live, String format, byte[] data, int qos, boolean retained) throws MqttPersistenceException, MqttException {
		if(cameraId.indexOf("/") != -1) {
			cameraId = cameraId.replaceAll("/", "_");
		}
		final String topic = getStreamTopic(cameraId, live, format);
		LOG.info("sending video size: {}, topic: {}", data.length, topic);
		mqtt.publish(topic, data, qos, retained);
	}
	
	protected String getDeviceTopic() {
		return String.format("/%s/device/%s/cmds", domainKey, deviceId);
	}

	/**
	 * /{domain-key}/device/{device-id}/msgs/{message-id}/{device-model}/{firmware-version}
	 */
	protected String getTopic(int messageId) {
		return String.format("/%s/device/%s/msgs/%d/%s/%s", domainKey, deviceId, messageId, deviceModel, firmwareVersion);
	}
	
	/**
	 * /{domain-key}/device/{device-id}/snap/{live|offline}/{camera-id}/{format (png|jpg|bmp...)}
	 */
	protected String getSnapTopic(String cameraId, boolean live, String format) {
		return String.format("/%s/device/%s/snap/%s/%s/%s", domainKey, deviceId, live ? "live" : "offline", cameraId, format);
	}
	
	/**
	 * /{domain-key}/device/{device-id}/stream/{live|offline}/{camera-id}/{format (video formats...)}
	 */
	protected String getStreamTopic(String cameraId, boolean live, String format) {
		return String.format("/%s/device/%s/stream/%s/%s/%s", domainKey, deviceId, live ? "live" : "offline", cameraId, format);
	}
	
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		byte[] raw = message.getPayload();
		processData(raw, 0, raw.length);
	}

}
