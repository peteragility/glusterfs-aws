package com.myorg;

import software.amazon.awscdk.services.ec2.EbsDeviceVolumeType;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;

public class GlusterfsPara {
    public static enum GlusterVolumeType {
        REPLICATED, DISTRIBUTED, DISPERSED
    }

    private String amiName = "ubuntu/images/hvm-ssd/ubuntu-bionic-18.04*";
    private String vpcTagName = "glusterfsVpc";
    private int glusterMountPort = 24007;
    private EbsDeviceVolumeType volumeType = EbsDeviceVolumeType.GP2;

    private String accountId;
    private String regionCode = "ap-east-1";

    private InstanceType instanceType = InstanceType.of(InstanceClass.COMPUTE5, InstanceSize.LARGE);
    private GlusterVolumeType glusterVolType = GlusterVolumeType.DISPERSED;
    private int brickCount = 3;
    private int redundancyCount = 1;

    private int volumeSizeGb = 50;
    private int volumeIops = volumeSizeGb * 50;
    private boolean volumeEncrypted = false;

    public String getAmiName() {
        return amiName;
    }

    public void setAmiName(String amiName) {
        this.amiName = amiName;
    }

    public String getVpcTagName() {
        return vpcTagName;
    }

    public void setVpcTagName(String vpcTagName) {
        this.vpcTagName = vpcTagName;
    }

    public int getGlusterMountPort() {
        return glusterMountPort;
    }

    public void setGlusterMountPort(int glusterMountPort) {
        this.glusterMountPort = glusterMountPort;
    }

    public EbsDeviceVolumeType getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(EbsDeviceVolumeType volumeType) {
        this.volumeType = volumeType;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public InstanceType getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(InstanceType instanceType) {
        this.instanceType = instanceType;
    }

    public GlusterVolumeType getGlusterVolType() {
        return glusterVolType;
    }

    public void setGlusterVolType(GlusterVolumeType glusterVolType) {
        this.glusterVolType = glusterVolType;
    }

    public int getBrickCount() {
        return brickCount;
    }

    public void setBrickCount(int brickCount) {
        this.brickCount = brickCount;
    }

    public int getRedundancyCount() {
        return redundancyCount;
    }

    public void setRedundancyCount(int redundancyCount) {
        this.redundancyCount = redundancyCount;
    }

    public int getVolumeSizeGb() {
        return volumeSizeGb;
    }

    public void setVolumeSizeGb(int volumeSizeGb) {
        this.volumeSizeGb = volumeSizeGb;
    }

    public int getVolumeIops() {
        return volumeIops;
    }

    public void setVolumeIops(int volumeIops) {
        this.volumeIops = volumeIops;
    }

    public boolean isVolumeEncrypted() {
        return volumeEncrypted;
    }

    public void setVolumeEncrypted(boolean volumeEncrypted) {
        this.volumeEncrypted = volumeEncrypted;
    }

}