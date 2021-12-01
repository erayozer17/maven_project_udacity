package org.example.catpoint.security.service;

import org.example.catpoint.image.service.ImageService;
import org.example.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private final String randomUUID = UUID.randomUUID().toString();

    private Sensor sensor;

    private
    SecurityService securityService;

    @Mock
    SecurityRepository repository;

    @Mock
    ImageService imageService;

    private Sensor getSensor(){
        return new Sensor(randomUUID, SensorType.DOOR);
    }

    @BeforeEach
    private void setUp() {
        securityService = new SecurityService(repository, imageService);
        sensor = getSensor();
    }

    static Stream<Arguments> getBooleanStream(){
        return Stream.of(Arguments.of(false, true), Arguments.of(true, false));
    }

    private Set<Sensor> getSensors(boolean active, int count){
        String randomString = UUID.randomUUID().toString();
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i <= count; i++){
            sensors.add(new Sensor(randomString, SensorType.DOOR));
        }
        sensors.forEach(it -> it.setActive(active));
        return sensors;
    }

    @Test
    public void changeAlarmStatusAlarmAlreadyPendingAndSensorActivatedAlarmStatusAlarm(){
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(repository, atMost(2)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    public void changeAlarmStatusSystemDisArmedChangeToAlarmStatus(){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(repository, atMostOnce()).setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.NO_ALARM);
    }

    @Test
    public void changeAlarmStateAlarmActiveAndSensorStateChangesStateNotAffected() {
        sensor.setActive(false);
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(repository, never()).setAlarmStatus(any(AlarmStatus.class));
        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(repository, atMostOnce()).updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);

        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(repository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(repository, atMost(2)).updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);
    }

    @Test
    public void changeAlarmStateSystemActivatedWhileAlreadyActiveAndAlarmPendingChangeToAlarmState(){
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(repository, atMostOnce()).setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @MethodSource("getBooleanStream")
    public void changeAlarmStatusSensorStatusChangeAndSystemIsAlreadyDisarmedStateNotAffected(Boolean b1, Boolean b2){
        sensor.setActive(b1);
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.changeSensorActivationStatus(sensor, b2);
        verify(repository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(repository, atMostOnce()).updateSensor(sensor);
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    public void changeAlarmStateSensorDeactivateWhileInactiveNoChangeToAlarmState(AlarmStatus alarmStatus){
        securityService.changeSensorActivationStatus(sensor, false);
        verify(repository, never()).setAlarmStatus(any());
    }

    @Test
    public void changeAlarmStateImageContainingCatDetectedAndSystemArmedChangeToAlarmStatus(){
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> alarmStatusArgumentCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(repository, atMostOnce()).setAlarmStatus(alarmStatusArgumentCaptor.capture());
        assertEquals(alarmStatusArgumentCaptor.getValue(), AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    public void changeAlarmStatusAlarmArmedAndSensorActivatedAlarmStatusPending(ArmingStatus armingStatus){
        when(securityService.getArmingStatus()).thenReturn(armingStatus);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        ArgumentCaptor<AlarmStatus> alarmStatusArgumentCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(repository, atMostOnce()).setAlarmStatus(alarmStatusArgumentCaptor.capture());
        assertEquals(alarmStatusArgumentCaptor.getValue(), AlarmStatus.PENDING_ALARM);
    }

    @Test
    public void changeAlarmStateNoCatImageIdentifiedAndSensorsAreInactiveChangeToAlarmStatus(){
        Set<Sensor> inactiveSensors = getSensors(false, 4);
        when(repository.getSensors()).thenReturn(inactiveSensors);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> alarmStatusArgumentCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(repository, atMostOnce()).setAlarmStatus(alarmStatusArgumentCaptor.capture());
        assertEquals(alarmStatusArgumentCaptor.getValue(), AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    public void updateSensorsSystemArmedDeactivateAllSensors(ArmingStatus armingStatus){
        // If the system is armed, reset all sensors to inactive. (Case 10)
        Set<Sensor> sensors = getSensors(true, 4);
        when(repository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(armingStatus);
        List<Executable> executableList = new ArrayList<>();
        sensors.forEach(it -> executableList.add(() -> assertEquals(it.getActive(), false)));
        assertAll(executableList);
    }

    @Test
    public void changeAlarmStatusSystemArmedHomeAndCatDetectedChangeToAlarmStatus(){
        //If the system is armed-home while the camera shows a cat, set the alarm status to alarm.(Case 11)
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> alarmStatusArgumentCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(repository, atMostOnce()).setAlarmStatus(alarmStatusArgumentCaptor.capture());
        assertEquals(alarmStatusArgumentCaptor.getValue(), AlarmStatus.ALARM);
    }

    @Test
    public void changeAlarmStatusAlarmPendingAndAllSensorsInactiveChangeToNoAlarm(){
        Set<Sensor> allSensors = getSensors(false, 4);
        Sensor last = allSensors.iterator().next();
        last.setActive(true);
        when(repository.getSensors()).thenReturn(allSensors);
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(last, false);
        ArgumentCaptor<AlarmStatus> alarmStatusArgumentCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(repository, atMostOnce()).setAlarmStatus(alarmStatusArgumentCaptor.capture());
        assertEquals(alarmStatusArgumentCaptor.getValue(), AlarmStatus.NO_ALARM);
    }
}
