package com.myorg;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;

public final class GlusterfsAwsApp {
    public static void main(final String[] args) {
        App app = new App();

        // Define glusterfs parameters here
        GlusterfsPara paras = new GlusterfsPara();
        paras.setAccountId("aws-account-id");
        paras.setRegionCode("ap-east-1");
        paras.setVpcTagName("glusterfsVpc");
        paras.setAmiName("ubuntu/images/hvm-ssd/ubuntu-bionic-18.04*");
        paras.setInstanceType(InstanceType.of(InstanceClass.COMPUTE5, InstanceSize.LARGE));
        paras.setGlusterVolType(GlusterfsPara.GlusterVolumeType.REPLICATED);
        paras.setBrickCount(3);
        paras.setRedundancyCount(1);
        paras.setVolumeSizeGb(60);
        paras.setVolumeEncrypted(false);
        // Define parameters ended

        StackProps envProps = StackProps.builder().env(Environment.builder()
                .account(paras.getAccountId()).region(paras.getRegionCode()).build()).build();

        new GlusterfsAwsStack(app, "GlusterfsAwsStack", envProps, paras);

        app.synth();
    }
}
