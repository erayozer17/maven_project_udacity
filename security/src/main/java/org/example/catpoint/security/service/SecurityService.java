package org.example.catpoint.security.service;


import org.example.catpoint.image.service.ImageService;
import org.example.catpoint.security.application.StatusListener;
import org.example.catpoint.security.data.AlarmStatus;
import org.example.catpoint.security.data.ArmingStatus;
import org.example.catpoint.security.data.SecurityRepository;
import org.example.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private ImageService imageService;
    private SecurityRepository securityRepository;
    private Set<StatusListener> statusListeners = new HashSet<>();
    private boolean isContainsCat = false;


    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    Set<Sensor> getActiveSensors(){
        return getSensors().stream().filter(Sensor::getActive).collect(Collectors.toSet());
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if(armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else if (isSystemArmedAwayOrArmedHome(armingStatus)){
            if (isContainsCat){
                setAlarmStatus(AlarmStatus.ALARM);
            }
            setActivationFalseForSensors(this.getActiveSensors());
        }
        securityRepository.setArmingStatus(armingStatus);
        statusListeners.forEach(StatusListener::sensorStatusChanged);
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        boolean wasActive = sensor.getActive();
        Boolean activate = !wasActive && active;
        Boolean deactivate = wasActive && !active;

        if (activate || deactivate){
            if (activate){
                handleSensorActivated();
                System.out.println("Sensor activated " + sensor.getSensorId());
            } else {
                handleSensorDeactivated(sensor);
                System.out.println("Sensor deactivated " + sensor.getSensorId());
            }
        }
        sensor.setActive(active);

        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        isContainsCat = imageService.imageContainsCat(currentCameraImage, 50.0f);
        catDetected(isContainsCat);
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }

    private void setActivationFalseForSensors(Set<Sensor> sensors) {
        ConcurrentSkipListSet<Sensor> sensorCopies = new ConcurrentSkipListSet<>(sensors);
        Iterator<Sensor> iterator = sensorCopies.iterator();
        while (iterator.hasNext()){
            Sensor sensor = iterator.next();
            sensor.setActive(true);
            changeSensorActivationStatus(sensor, false);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     * change in sensor state should not affect the alarm if it was already active
     */
    private void handleSensorDeactivated(Sensor sensor) {
        if (ArmingStatus.DISARMED == securityRepository.getArmingStatus()){
            return;
        }
        if (securityRepository.getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
            Set<Sensor> sensors = getSensors();
            sensors.remove(sensor);
            if (allSensorsInactive()){
                setAlarmStatus(AlarmStatus.NO_ALARM);
            }
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return;
        }
        AlarmStatus alarmStatus = securityRepository.getAlarmStatus();
        switch(alarmStatus) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);

            default -> {}
        }
    }

    private boolean allSensorsInactive() {
        return getSensors().stream().noneMatch(Sensor::getActive);
    }

    private boolean isSystemArmedAwayOrArmedHome(ArmingStatus armingStatus){
        return List.of(ArmingStatus.ARMED_HOME, ArmingStatus.ARMED_AWAY).contains(armingStatus);
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        if(cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (!cat && allSensorsInactive()){
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
        statusListeners.forEach(sl -> sl.catDetected(cat));
    }
}
