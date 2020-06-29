## GlusterFS on AWS

This Quick Start deploys GlusterFS v7 on the Amazon Web Services (AWS) Cloud.

GlusterFS is a scalable network filesystem suitable for data-intensive tasks such as cloud storage and media streaming. GlusterFS is free and open source software and can utilize common off-the-shelf hardware. To learn more, please see [the Gluster project home page](https://www.gluster.org/).

Setup a GlusterFS cluster is not an easy task, by using this quick start which is based on AWS Cloud Development Kit (AWS CDK), a Gluster file system can be spinned up in AWS in minutes and clients can access the file system by native Gluster mount or NFSv3 mount.

Notice that ongoing maintenace of a Gluster cluster become your responsbility, like adding new bricks, replacing faulty bricks, backup/snapshot, detail documentation can be found [here](https://docs.gluster.org/en/latest/Administrator%20Guide/overview/). This is why fully managed file system like [Amazon EFS](https://aws.amazon.com/efs/) is always preferred, however there are use cases that GlusterFS maybe more suitable, we will mention that later.

The quick start implements the followings:

- Deploy Ubuntu 18.04 EC2s with EBS to selected VPC, as Gluster compute/storage nodes
- Setup Gluster system in every node, according to user selected Gluster volume type (distributed, dispersed or replicated)
- Deploy a Network Load Balancer (NLB) in front of the cluster, listening on Gluster control port: 24007 
- Deploy a Gluster security group, need to add client machines to this security group for them to mount Gluster file system

### Index

- [Use Cases](#use-cases)
- [Architecture](#architecture)
- [Usage](#usage)
  - [Prerequisites](#prerequisites)
  - [Deployment](#deployment)
  - [Mount GlusterFS by Native Client](#mount-glusterfs-by-native-client)
  - [Mount GlusterFS by NFSv3](#mount-glusterfs-by-nfsv3)
- [Performance Testing](#performance-testing)
- [Making changes to the code and customization](#making-changes-to-the-code-and-customization)
- [Contributing](#contributing)

### Use Cases

- **Cost effectiveness.** Provision a GlusterFS gives you the flexibility to choose the EC2 instance type, EBS volume type and no. of replica nodes, lower per GB/month price can be achieved if there is lower requriement on data durability/availability. Beware that EBS charges for provisioned storage while Amazon EFS only charges for actual storage used.
- **Performance.** By choosing appropriate EC2/EBS types and Gluster volume type, high performance and cost effective Gluster filesystem can be built. For fully managed high performance shared storage, remmeber to have a look at [Amazon FSx for Lustre](https://aws.amazon.com/fsx).
- **Service Availability.** In some AWS regions or in AWS Outposts, where Amazon EFS not yet available, you can build a GlusterFS using EC2/EBS for shared file storage.
- **Lift and Shift GlusterFS to AWS.** If you are already using GlusterFS on-premises or in other clouds, and want the quickest way to migrate to AWS.

### Usage

#### Prerequisites

To deploy the GlusterFS quick start, you will require an AWS account, prepare a VPC tagged with glusterfsVpc=true and private subnets that have outbound internet access only, GlusterFS will be automatically setup in the VPC and subnets.

When ECs are launched they need to download and install Gluster related packages, internet access can be turned off after GlusterFS is setup, to ensure the cluster run in a private protected network.

#### Deployment
1. Install AWS CLI v2 in your workstation, according to this [guide](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html).
2. Configure AWS credentials and default region by:
    ```
    aws configure
    ```
3. Install Java 8 or later and Apache Maven 3.5 or later.
4. Install node/npm in your workstation.
5. Install AWS CDK by:
    ```
    npm install -g aws-cdk
    ```
6. Run the following command to verify correct installation and print the version number of the AWS CDK:
    ```
    cdk --version
    ```
7. Git clone this repo to your workstation, cd to the project folder.
8. Modify Gluster setup parameters in [GlusterfsAwsApp.java](src/main/java/com/myorg/GlusterfsAwsApp.java) to suit your needs, here is the parameter table: 

| Parameters      | Description                                         | Default         | Possible Value                                                   |
|-----------------|-----------------------------------------------------|-----------------|------------------------------------------------------------------|
| accountId       | 12-Digit AWS Account ID                             |                 | 12-digit string                                                  |
| regionCode      | AWS Region                                          | ap-east-1       | AWS region code                                                  |
| instanceType    | EC2 Instance Type                                   | c5.large        | c5, c5n, m5, m5n, r5, r5n series recommended                          |
| volumeType      | EBS Volume Type                                     | gp2             | gp2, io1, st1, sc1                                                  |
| volumeSizeGb    | EBS Volume Size (in GB)                             | 50              | up to 16,000 (16TB)                                              |
| volumeEncrypted | EBS Encryption with KMS                             | false           | false, true                                                      |
| volumeIops      | EBS Provisioned IOPS for io1 EBS Volume             | 50*volumeSizeGb | Max 50*volumeSizeGb                                              |
| glusterVolType  | Gluster Volume Type                                 | dispersed       | distributed, replicated, dispersed                               |
| brickCount      | Gluster Brick Count (one EC2/EBS per brick)         | 3               | min 1 for distributed, min 2 for replicated, min 3 for dispersed |
| redundancyCount | Gluster Brick Redundancy Count for Dispersed Volume | 1               | max value < (brickCount / 2)                                     |

9. Compile the project and download all dependancies by Maven:
   ```
   mvn package
   ```
10. Deploy to AWS!
    ```
    cdk deploy
    ```
    Other useful cdk commands:
    * `cdk ls`          list all stacks in the app
    * `cdk synth`       emits the synthesized CloudFormation template
    * `cdk diff`        compare deployed stack with current state

11. While the deployment is running, you can login AWS console and goto Cloudformation and check the stack progress.
12. After cdk deployment completed, pls drop down the following outputs of Cloudformation stack:
    - **GlusterNLBEndpoint** <-- this is the Gluster native mount point
    - **GlusterNFSEndpoint** <-- this is the NFSv3 mount point

#### Mount GlusterFS by Native Client

#### Mount GlusterFS by NFSv3

### Performance Testing