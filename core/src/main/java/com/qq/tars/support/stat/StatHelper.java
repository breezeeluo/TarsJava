/**
 * Tencent is pleased to support the open source community by making Tars available.
 * <p>
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 * <p>
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://opensource.org/licenses/BSD-3-Clause
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.qq.tars.support.stat;

import com.qq.tars.client.Communicator;
import com.qq.tars.client.util.ClientLogger;
import com.qq.tars.common.util.StringUtils;
import com.qq.tars.support.stat.prx.StatFPrx;
import com.qq.tars.support.stat.prx.StatFPrxCallback;
import com.qq.tars.support.stat.prx.StatMicMsgBody;
import com.qq.tars.support.stat.prx.StatMicMsgHead;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

public final class StatHelper {

    private final static int BATCH_REPORTS = 10;

    private final Communicator communicator;

    public StatHelper(Communicator communicator) {
        this.communicator = communicator;
    }

    private StatFPrx getPrx() {
        StatFPrx prx = null;
        String stat = communicator.getCommunicatorConfig().getStat();
        if (!StringUtils.isEmpty(stat)) {
            prx = communicator.stringToProxy(StatFPrx.class, communicator.getCommunicatorConfig().getStat());
        }
        return prx;
    }

    public void report(ProxyStat proxyStat, boolean bFromClient) {
        StatFPrx statFProxy = getPrx();
        if (statFProxy == null) {
            ClientLogger.getLogger().info("no config stat obj to report");
            return;
        }
        if (proxyStat.isEmpty()) {
            ClientLogger.getLogger().info("no stat need to report");
            return;
        }
        long start = System.currentTimeMillis();
        int i = 0, errorCount = 0, successCount = 0;
        try {
            HashMap<StatMicMsgHead, StatMicMsgBody> reprotMap = new HashMap<StatMicMsgHead, StatMicMsgBody>();
            for (Entry<ProxyStatHead, ProxyStatBody> statHead : proxyStat.getStats().entrySet()) {

                ProxyStatHead head = statHead.getKey();
                ProxyStatBody body = statHead.getValue();

                HashMap<Integer, Integer> intervalCount = new HashMap<Integer, Integer>();
                for (Entry<Integer, AtomicInteger> interval : body.intervalCount.entrySet()) {
                    intervalCount.put(interval.getKey(), interval.getValue().get());
                }
                StatMicMsgBody mbody = new StatMicMsgBody(body.getCount(), body.getTimeoutCount(), body.getExecCount(), intervalCount, body.getTotalRspTime(), body.getMaxRspTime(), body.getMinRspTime());
                body.clear();

                StatMicMsgHead mHead = new StatMicMsgHead(head.getMasterName(), head.getSlaveName(), head.getInterfaceName(), head.getMasterIp(), head.getSlaveIp(), head.getSlavePort(), head.getReturnValue(), head.getSlaveSetName(), head.getSlaveSetArea(), head.getSlaveSetID(), head.getTafVersion());
                reprotMap.put(mHead, mbody);

                ClientLogger.getLogger().info("report call|" + statHead.getKey().masterName + "|" + statHead.getKey().slaveIp + ":" + statHead.getKey().slavePort + "|" + statHead.getKey().slaveName + "." + statHead.getKey().interfaceName + "_" + statHead.getKey().getReturnValue() + "(" + statHead.getKey().slaveSetName + "." + statHead.getKey().slaveSetArea + "." + statHead.getKey().slaveSetID + "):" + mbody.count + "_" + mbody.execCount + "_" + mbody.timeoutCount + "_" + mbody.totalRspTime + "_" + mbody.maxRspTime + "_" + mbody.minRspTime);
                i++;
                if (i % BATCH_REPORTS == 0) {
                    try {
                        //statFProxy.reportMicMsg(reprotMap, true);
                        statFProxy.async_reportMicMsg(new StatFPrxCallback()
                        {
                            
                            @Override
                            public void callback_expired()
                            {
                                ClientLogger.getLogger().warn("i % BATCH_REPORTS == 0 . 上报异超时.....");
                                
                            }
                            
                            @Override
                            public void callback_exception(Throwable ex)
                            {
                                ClientLogger.getLogger().error("i % BATCH_REPORTS == 0 . 上报异 msg : " + ex.getMessage() , ex);
                            }
                            
                            @Override
                            public void callback_reportSampleMsg(int ret)
                            {
                                // TODO Auto-generated method stub
                                
                            }
                            
                            @Override
                            public void callback_reportMicMsg(int ret)
                            {
                                // TODO Auto-generated method stub
                                
                            }
                        }, reprotMap, bFromClient);
                        ++successCount;
                    } catch (Exception e) {
                        ClientLogger.getLogger().error("error occurred on report proxy stat", e);
                        ++errorCount;
                    }
                    reprotMap = new HashMap<StatMicMsgHead, StatMicMsgBody>();
                }
            }
            if (reprotMap.size() > 0) {
                try {
                    //statFProxy.reportMicMsg(reprotMap, true);
                    statFProxy.async_reportMicMsg(new StatFPrxCallback()
                    {
                        
                        @Override
                        public void callback_expired()
                        {
                            ClientLogger.getLogger().warn("reprotMap.size() > 0 . 上报异超时.....");
                            
                        }
                        
                        @Override
                        public void callback_exception(Throwable ex)
                        {
                            ClientLogger.getLogger().error("reprotMap.size() > 0 . 上报异 msg : " + ex.getMessage() , ex);
                        }
                        
                        @Override
                        public void callback_reportSampleMsg(int ret)
                        {
                            // TODO Auto-generated method stub
                            
                        }
                        
                        @Override
                        public void callback_reportMicMsg(int ret)
                        {
                            // TODO Auto-generated method stub
                            
                        }
                    }, reprotMap, bFromClient);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    ClientLogger.getLogger().error("error occurred on report proxy stat", e);
                }
            }
        } catch (Exception e) {
            ClientLogger.getLogger().error("error occurred on report proxy stat", e);
        } finally {
            ClientLogger.getLogger().info("report success:" + successCount + " fail:" + errorCount + " costTime:" + (System.currentTimeMillis() - start));
        }
    }
}
