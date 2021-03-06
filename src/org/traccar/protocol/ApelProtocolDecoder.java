/*
 * Copyright 2013 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.AdvancedConnection;
import org.traccar.helper.Log;
import org.traccar.helper.NamedParameterStatement;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class ApelProtocolDecoder extends BaseProtocolDecoder {

    private long deviceId;
    private long lastIndex;
    private long newIndex;

    public ApelProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    /*
     * Message types
     */
    /*private static final int MSG_NULL = 0;
    private static final int MSG_IDENT = 1;
    private static final int MSG_IDENT_FULL = 2;
    private static final int MSG_POINT = 10;
    private static final int MSG_LOG_SYNC = 100;
    private static final int MSG_LOGMSG = 101;
    private static final int MSG_TEXT = 102;
    private static final int MSG_ALARM = 200;
    private static final int MSG_ALARM_RECIEVED = 201;*/
    private static final int MSG_TYPE_NULL = 0;
    private static final int MSG_TYPE_REQUEST_TRACKER_ID = 10;
    private static final int MSG_TYPE_TRACKER_ID = 11;
    private static final int MSG_TYPE_TRACKER_ID_EXT = 12;
    private static final int MSG_TYPE_DISCONNECT = 20;
    private static final int MSG_TYPE_REQUEST_PASSWORD = 30;
    private static final int MSG_TYPE_PASSWORD = 31;
    private static final int MSG_TYPE_REQUEST_STATE_FULL_INFO = 90;
    private static final int MSG_TYPE_STATE_FULL_INFO_T104 = 92;
    private static final int MSG_TYPE_REQUEST_CURRENT_GPS_DATA = 100;
    private static final int MSG_TYPE_CURRENT_GPS_DATA = 101;
    private static final int MSG_TYPE_REQUEST_SENSORS_STATE = 110;
    private static final int MSG_TYPE_SENSORS_STATE = 111;
    private static final int MSG_TYPE_SENSORS_STATE_T100 = 112;
    private static final int MSG_TYPE_SENSORS_STATE_T100_4 = 113;
    private static final int MSG_TYPE_REQUEST_LAST_LOG_INDEX = 120;
    private static final int MSG_TYPE_LAST_LOG_INDEX = 121;
    private static final int MSG_TYPE_REQUEST_LOG_RECORDS = 130;
    private static final int MSG_TYPE_LOG_RECORDS = 131;
    private static final int MSG_TYPE_EVENT = 141;
    private static final int MSG_TYPE_TEXT = 150;
    private static final int MSG_TYPE_ACK_ALARM = 160;
    private static final int MSG_TYPE_SET_TRACKER_MODE = 170;
    private static final int MSG_TYPE_GPRS_COMMAND = 180;

    private static final String HEX_CHARS = "0123456789ABCDEF";

    /*private void loadLastIndex() {
        try {
            Properties p = getServerManager().getProperties();
            if (p.contains("database.selectLastIndex")) {
                AdvancedConnection connection = new AdvancedConnection(
                        p.getProperty("database.url"), p.getProperty("database.user"), p.getProperty("database.password"));
                NamedParameterStatement queryLastIndex = new NamedParameterStatement(connection, p.getProperty("database.selectLastIndex"));
                queryLastIndex.prepare();
                queryLastIndex.setLong("device_id", deviceId);
                ResultSet result = queryLastIndex.executeQuery();
                if (result.next()) {
                    lastIndex = result.getLong(1);
                }
            }
        } catch(Exception error) {
        }
    }*/

    /*private void requestArchive(Channel channel) {
        if (lastIndex == 0) {
            lastIndex = newIndex;
        } else if (newIndex > lastIndex) {
            ChannelBuffer request = ChannelBuffers.directBuffer(ByteOrder.LITTLE_ENDIAN, 12);
            request.writeShort(MSG_LOG_SYNC);
            request.writeShort(4);
            request.writeInt((int) lastIndex);
            request.writeInt(0);
            channel.write(request);
        }
    }*/

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        ChannelBuffer buf = (ChannelBuffer) msg;
        int type = buf.readUnsignedShort();
        boolean alarm = (type & 0x8000) != 0;
        type = type & 0x7FFF;
        buf.readUnsignedShort(); // length
        
        if (type == MSG_TYPE_TRACKER_ID) {
            Log.warning("Unsupported authentication type");
            return null;
        }

        if (type == MSG_TYPE_TRACKER_ID_EXT) {
            long id = buf.readUnsignedInt();
            int length = buf.readUnsignedShort();
            buf.skipBytes(length);
            length = buf.readUnsignedShort();
            String imei = buf.readBytes(length).toString(Charset.defaultCharset());
            try {
                deviceId = getDataManager().getDeviceByImei(imei).getId();
                //loadLastIndex();
            } catch(Exception error) {
                Log.warning("Unknown device - " + imei + " (id - " + id + ")");
            }
        }

        // Position
        else if (deviceId != 0 && (type == MSG_TYPE_CURRENT_GPS_DATA || type == MSG_TYPE_STATE_FULL_INFO_T104)) {
            List<Position> positions = new LinkedList<Position>();

            int recordCount = 1;
            /*if (type == MSG_LOGMSG) {
                recordCount = buf.readUnsignedShort();
            }*/

            for (int j = 0; j < recordCount; j++) {
                Position position = new Position();
                ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("apel");
                position.setDeviceId(deviceId);

                // Message index
                /*if (type == MSG_LOGMSG) {
                    extendedInfo.set("archive", true);
                    int subtype = buf.readUnsignedShort();
                    if (subtype == MSG_ALARM) {
                        extendedInfo.set("alarm", true);
                    }
                    if (buf.readUnsignedShort() > buf.readableBytes()) {
                        lastIndex += 1;
                        break; // workaround for device bug
                    }
                    lastIndex = buf.readUnsignedInt();
                    position.setId(lastIndex);
                } else {
                    newIndex = buf.readUnsignedInt();
                }*/

                // Time
                Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                time.clear();
                time.setTimeInMillis(buf.readUnsignedInt() * 1000);
                position.setTime(time.getTime());

                // Latitude
                position.setLatitude(buf.readInt() * 180.0 / 0x7FFFFFFF);

                // Longitude
                position.setLongitude(buf.readInt() * 180.0 / 0x7FFFFFFF);

                // Speed and Validity
                if (type == MSG_TYPE_CURRENT_GPS_DATA) {
                    int speed = buf.readShort();
                    position.setValid(speed != -1);
                    position.setSpeed(speed / 100.0 * 0.539957);
                } else if (type == MSG_TYPE_STATE_FULL_INFO_T104) {
                    int speed = buf.readUnsignedByte();
                    position.setValid(speed != 255);
                    position.setSpeed(speed * 0.539957);
                    extendedInfo.set("hdop", buf.readByte());
                }

                // Course
                position.setCourse(buf.readShort() / 100.0);

                // Altitude
                position.setAltitude((double) buf.readShort());

                if (type == MSG_TYPE_STATE_FULL_INFO_T104) {

                    // Satellites
                    extendedInfo.set("satellites", buf.readUnsignedByte());
                    
                    // Cell signal
                    extendedInfo.set("gsm", buf.readUnsignedByte());

                    // Event type
                    extendedInfo.set("event", buf.readUnsignedShort());

                    // Milage
                    extendedInfo.set("milage", buf.readUnsignedInt());

                    // Input/Output
                    extendedInfo.set("input", buf.readUnsignedByte());
                    extendedInfo.set("output", buf.readUnsignedByte());
                    
                    // Analog sensors
                    for (int i = 1; i <= 8; i++) {
                        extendedInfo.set("adc" + i, buf.readUnsignedShort());
                    }
                    
                    // Counters
                    extendedInfo.set("c0", buf.readUnsignedInt());
                    extendedInfo.set("c1", buf.readUnsignedInt());
                    extendedInfo.set("c2", buf.readUnsignedInt());
                    
                    /*long extraFlags = buf.readLong();

                    // Analog inputs
                    if ((extraFlags & 0x1) == 0x1) {
                        int count = buf.readUnsignedShort();
                        for (int i = 1; i <= count; i++) {
                            extendedInfo.set("adc" + i, buf.readUnsignedShort());
                        }

                    }

                    // CAN adapter
                    if ((extraFlags & 0x2) == 0x2) {
                        int size = buf.readUnsignedShort();
                        extendedInfo.set("can", buf.toString(buf.readerIndex(), size, Charset.defaultCharset()));
                        buf.skipBytes(size);
                    }

                    // Passenger sensor
                    if ((extraFlags & 0x4) == 0x4) {
                        int size = buf.readUnsignedShort();

                        // Convert binary data to hex
                        StringBuilder hex = new StringBuilder();
                        for (int i = buf.readerIndex(); i < buf.readerIndex() + size; i++) {
                            byte b = buf.getByte(i);
                            hex.append(HEX_CHARS.charAt((b & 0xf0) >> 4));
                            hex.append(HEX_CHARS.charAt((b & 0x0F)));
                        }

                        extendedInfo.set("passenger", hex);

                        buf.skipBytes(size);
                    }

                    // Send response for alarm message
                    if (type == MSG_ALARM) {
                        byte[] response = {(byte)0xC9,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
                        channel.write(ChannelBuffers.wrappedBuffer(response));

                        extendedInfo.set("alarm", true);
                    }*/
                }

                // Skip CRC
                buf.readUnsignedInt();

                // Extended info
                position.setExtendedInfo(extendedInfo.toString());

                positions.add(position);
            }

            //requestArchive(channel);

            return positions;
        }

        return null;
    }

}
