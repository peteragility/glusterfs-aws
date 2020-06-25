package com.myorg;

import java.util.Map;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.ConcreteDependable;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.BlockDevice;
import software.amazon.awscdk.services.ec2.BlockDeviceVolume;
import software.amazon.awscdk.services.ec2.EbsDeviceOptions;
import software.amazon.awscdk.services.ec2.EbsDeviceVolumeType;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Instance;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.LookupMachineImageProps;
import software.amazon.awscdk.services.ec2.MachineImage;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.UserData;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.INetworkTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.elasticloadbalancingv2.targets.InstanceIdTarget;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

public class GlusterfsAwsStack extends Stack {
    private static enum GlusterVolumeType {
        REPLICATED,
        DISTRIBUTED,
        DISPERSED
    }

    public GlusterfsAwsStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public GlusterfsAwsStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        // Define the parameters++
        final String vpcTagName = "glusterfsVpc";
        final String amiName = "ubuntu/images/hvm-ssd/ubuntu-xenial-16.04*";
        final InstanceType instanceType = InstanceType.of(InstanceClass.COMPUTE5, InstanceSize.LARGE);
        final GlusterVolumeType glusterVolType = GlusterVolumeType.DISPERSED;
        final int brickCount = 3;
        final int redundancyCount = 1;
        final EbsDeviceVolumeType volumeType = EbsDeviceVolumeType.GP2;
        final int volumeSizeGb = 50;
        final int volumeIops = volumeSizeGb * 50;
        final boolean volumeEncrypted = false;
        final int glusterMountPort = 24007;
        // Define the parameters--

        Map<String, String> VpcTags = new HashMap<>();
        VpcTags.put(vpcTagName, "true");
        IVpc myVpc = Vpc.fromLookup(this, vpcTagName, new VpcLookupOptions.Builder().tags(VpcTags).build());

        List<IManagedPolicy> policies = new ArrayList<>();
        policies.add(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonEC2RoleforSSM"));
        Role GlusterNodeRole = Role.Builder.create(this, "GlusterNodeRole").managedPolicies(policies).assumedBy(ServicePrincipal.Builder.create("ec2.amazonaws.com").build()).build();

        SecurityGroup GlusterNodeSG = SecurityGroup.Builder.create(this, "GlusterNodeSG").allowAllOutbound(true).securityGroupName("GlusterNodeSG").vpc(myVpc).build();
        GlusterNodeSG.addIngressRule(GlusterNodeSG, Port.allTraffic());
        GlusterNodeSG.addIngressRule(Peer.ipv4(myVpc.getVpcCidrBlock()), Port.tcp(glusterMountPort));

        List<Instance> instanceList = new ArrayList<>();

        for (int i = 1; i <= brickCount; i++) {
            UserData userData = UserData.forLinux();
            userData.addCommands("sudo apt install software-properties-common",
                    "sudo add-apt-repository ppa:gluster/glusterfs-7 -y", 
                    "sudo apt update",
                    "sudo apt install glusterfs-server glusterfs-client glusterfs-common -y",
                    "sudo mkfs.xfs /dev/nvme1n1", 
                    "sudo mkdir /gluster",
                    "sudo sh -c \"echo '/dev/nvme1n1 /gluster xfs defaults 0 0' >> /etc/fstab\"", 
                    "sudo mount -a",
                    "sudo mkdir /gluster/brick", 
                    "sudo systemctl start glusterd");
                    
            // Echo the gluster volume type, brick count and redundancy count in each instance's userdata
            // Which can help to trigger re-creation of instance after these config updated
            userData.addCommands("echo " + glusterVolType.name(), 
                "echo " + brickCount,
                "echo " + redundancyCount);
            
            // Additional commands for the last node to setup glusterfs volume
            if(i == brickCount) {
                String createVolCmd = "";
                for (Instance inst : instanceList) {
                    userData.addCommands("sudo gluster peer probe " + inst.getInstancePrivateDnsName());
                    createVolCmd = createVolCmd + " " + inst.getInstancePrivateDnsName() + ":/gluster/brick";
                }
                createVolCmd = createVolCmd + " $(hostname):/gluster/brick";

                if(glusterVolType == GlusterVolumeType.DISTRIBUTED) {
                    userData.addCommands("sudo gluster volume create gfs" + createVolCmd);
                } else if(glusterVolType == GlusterVolumeType.DISPERSED) {
                    userData.addCommands("sudo gluster volume create gfs disperse " + brickCount 
                        + " redundancy " + redundancyCount + createVolCmd);
                } else {
                    userData.addCommands("sudo gluster volume create gfs replica " + brickCount + createVolCmd);
                }

                userData.addCommands("sudo gluster volume info");
                //userData.addCommands("sudo gluster volume set gfs nfs.disable off");
                userData.addCommands("sudo gluster volume start gfs");
            }

            BlockDevice brickBlock = null;
            if(volumeType == EbsDeviceVolumeType.IO1) {
                brickBlock = BlockDevice.builder().deviceName("/dev/sdb")
                    .volume(BlockDeviceVolume.ebs(volumeSizeGb, 
                        EbsDeviceOptions.builder().volumeType(volumeType).iops(volumeIops).encrypted(volumeEncrypted).build())).build();
            } else {
                brickBlock = BlockDevice.builder().deviceName("/dev/sdb")
                .volume(BlockDeviceVolume.ebs(volumeSizeGb, 
                    EbsDeviceOptions.builder().volumeType(volumeType).encrypted(volumeEncrypted).build())).build();
            }
            List<BlockDevice> blockList = new ArrayList<BlockDevice>();
            blockList.add(brickBlock);

            // Select the AZ for the instance
            List<String> selectedAz = new ArrayList<>();
            selectedAz.add(myVpc.getAvailabilityZones().get((i-1) % myVpc.getAvailabilityZones().size()));

            Instance myInstance = Instance.Builder.create(this, "node" + i).instanceName("GlusterFS Node " + i)
                    .instanceType(instanceType).vpc(myVpc)
                    .vpcSubnets(SubnetSelection.builder().onePerAz(true).subnetType(SubnetType.PRIVATE).availabilityZones(selectedAz).build())
                    .machineImage(MachineImage.lookup(LookupMachineImageProps.builder().name(amiName).build()))
                    .blockDevices(blockList)
                    .userData(userData)
                    .role(GlusterNodeRole)
                    .securityGroup(GlusterNodeSG)
                    .build();

            if(i == brickCount) {
                ConcreteDependable dependable = new ConcreteDependable();
                for(Instance inst : instanceList) {
                    dependable.add(inst);
                }
                myInstance.getNode().addDependency(dependable);
            }
            
            // Append instance private dns to the list
            instanceList.add(myInstance);
        }

        // Add network load balancer++
        NetworkLoadBalancer myNlb = NetworkLoadBalancer.Builder.create(this, "GlusterNLB")
            .crossZoneEnabled(false).internetFacing(false).loadBalancerName("GlusterNLB")
            .vpc(myVpc).vpcSubnets(SubnetSelection.builder().onePerAz(false).subnetType(SubnetType.PRIVATE).build())
            .build();

        NetworkTargetGroup targetGroup = NetworkTargetGroup.Builder.create(this, "GlusterTargetGroup")
            .port(glusterMountPort)
            .targetType(TargetType.INSTANCE)
            .targetGroupName("GlusterTargetGroup")
            .vpc(myVpc)
            .build();

        for(Instance instance : instanceList) {
            targetGroup.addTarget(new InstanceIdTarget(instance.getInstanceId()));
        }

        List<INetworkTargetGroup> targetGroupList = new ArrayList<>();
        targetGroupList.add(targetGroup);

        myNlb.addListener("GlusterListener", NetworkListenerProps.builder().port(glusterMountPort).loadBalancer(myNlb).defaultAction(NetworkListenerAction.forward(targetGroupList)).build());
        // Add network load balancer--

        // Output cfn results ++
        CfnOutput.Builder.create(this, "GlusterNlbEndpoint").value(myNlb.getLoadBalancerDnsName()).build();
        // Output cfn results --
    }
}