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

    public GlusterfsAwsStack(final Construct parent, final String id) {
        this(parent, id, null, new GlusterfsPara());
    }

    public GlusterfsAwsStack(final Construct parent, final String id, final StackProps props, final GlusterfsPara paras) {
        super(parent, id, props);

        final String vpcTagName = paras.getVpcTagName();
        final String amiName = paras.getAmiName();
        final InstanceType instanceType = paras.getInstanceType();
        final GlusterfsPara.GlusterVolumeType glusterVolType = paras.getGlusterVolType();
        final int brickCount = paras.getBrickCount();
        final int redundancyCount = paras.getRedundancyCount();
        final EbsDeviceVolumeType volumeType = paras.getVolumeType();
        final int volumeSizeGb = paras.getVolumeSizeGb();
        final int volumeIops = paras.getVolumeIops();
        final boolean volumeEncrypted = paras.isVolumeEncrypted();
        final int glusterMountPort = paras.getGlusterMountPort();

        // Optional: generate ssh key pair
        /*
        JSch jsch = new JSch();
        String sshPrivateKey = "";
        String sshPublicKey = "";
        try {
            KeyPair kpair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            kpair.writePrivateKey(bao);
            sshPrivateKey = bao.toString();
            bao = new ByteArrayOutputStream();
            kpair.writePublicKey(bao, "glusterfs-nodes");
            sshPublicKey = bao.toString();
        } catch (JSchException e) {
            e.printStackTrace();
        }
        */
        // ended

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
                    "sudo apt install glusterfs-server glusterfs-client glusterfs-common nfs-ganesha-gluster -y",
                    "sudo mkfs.xfs /dev/nvme0n1", 
                    "sudo mkdir /gluster",
                    "sudo sh -c \"echo '/dev/nvme0n1 /gluster xfs defaults 0 0' >> /etc/fstab\"", 
                    "sudo mount -a",
                    "sudo mkdir /gluster/brick", 
                    "sudo systemctl start glusterd");
                    
            // Insert ssh keys for cross node communications
            //userData.addCommands("sudo sh -c \"echo '" + sshPublicKey + "' >> /root/.ssh/authorized_keys\"");
            //userData.addCommands("sudo sh -c \"echo '" + sshPublicKey + "' >> /var/lib/glusterd/nfs/secret.pem.pub\"");
            //userData.addCommands("sudo sh -c \"echo '" + sshPrivateKey + "' >> /var/lib/glusterd/nfs/secret.pem\"");
            
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

                if(glusterVolType == GlusterfsPara.GlusterVolumeType.DISTRIBUTED) {
                    userData.addCommands("sudo gluster volume create gfs" + createVolCmd);
                } else if(glusterVolType == GlusterfsPara.GlusterVolumeType.DISPERSED) {
                    userData.addCommands("sudo gluster volume create gfs disperse " + brickCount 
                        + " redundancy " + redundancyCount + createVolCmd);
                } else {
                    userData.addCommands("sudo gluster volume create gfs replica " + brickCount + createVolCmd);
                }

                userData.addCommands("sudo gluster volume info");
                userData.addCommands("sudo gluster volume start gfs");

                // enable shared storage and nfs-ganesha
                userData.addCommands("sudo gluster volume set all cluster.enable-shared-storage enable",
                        "sleep 20",
                        "sudo cp /etc/ganesha/gluster.conf /etc/ganesha/ganesha.conf",
                        "sudo sh -c \"sed -i 's/testvol/gfs/g' /etc/ganesha/ganesha.conf\"",
                        "sudo sh -c \"sed -i 's/Squash.*/&\\n\\n\\t# NFS3 Only\\n\\tProtocols = \\\"3\\\";/' /etc/ganesha/ganesha.conf\"",
                        "sudo systemctl stop nfs-ganesha",
                        "sudo systemctl start nfs-ganesha");
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

                CfnOutput.Builder.create(this, "GlusterNFSEndpoint").value(myInstance.getInstancePrivateDnsName()).build();
            }
            
            // Append instance private dns to the list
            instanceList.add(myInstance);
        }

        // Add network load balancer++
        NetworkLoadBalancer myNlb = NetworkLoadBalancer.Builder.create(this, "GlusterNLB")
            .crossZoneEnabled(false).internetFacing(false).loadBalancerName("GlusterNLB")
            .vpc(myVpc).vpcSubnets(SubnetSelection.builder().onePerAz(true).subnetType(SubnetType.PRIVATE).build())
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

        CfnOutput.Builder.create(this, "GlusterNLBEndpoint").value(myNlb.getLoadBalancerDnsName()).build();
        // Add network load balancer--

    }
}