package com.example.alarm.consumer;

import com.tailf.ncs.alarmman.common.Alarm;
import com.tailf.ncs.alarmman.consumer.AlarmSource;
import com.tailf.ncs.ApplicationComponent;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class AlarmConsumer implements ApplicationComponent  {
    private static Logger log =
        LogManager.getLogger(AlarmConsumer.class);

    private AlarmSource alarmSource;

    public void init() {
        log.info ( " initializing alarm source");
        alarmSource = new AlarmSource();
    }

    public void finish() {

        alarmSource.stopListening();
        alarmSource = null;
        log.info("Consumer died!");
    }

    public void run() {
        log.info (" Thread => '" +  Thread.currentThread().getName() +
                  "' is running!");
        try {

            Alarm alarm = null;
            do {
                alarm = alarmSource.takeAlarm();
                log.info("ManagedDevice=" + alarm.getManagedDevice() );
                log.info("ManagedObject=" + alarm.getManagedObject() );
                log.info("AlarmType=" + alarm.getAlarmType() );
                log.info("SpecificProblem=" + alarm.getSpecificProblem() );
                log.info("Severity=" + alarm.getPerceivedSeverity() );
                log.info("AlarmText:" + alarm.alarmText() );
                log.info("Time:" + alarm.getTimeStamp());

            } while(!alarm.isLastAlarm());

            log.info("AlarmConsumer STOPPED!");
            log.info ( " running --> ok");

        } catch (InterruptedException  e) {
            log.warn(" Thread => '" + Thread.currentThread().getName() +
                     "' got interrupted!" +
                     " while waiting for alarm , redeploy may have " +
                     "been issued or NCS JVM may have been stopped!");

        } catch ( Exception e ) {
            log.error("",e);
        } finally {
            log.info (" Thread => " + Thread.currentThread().getName() +
                      " is aborting its execution!");
        }
    }
}
