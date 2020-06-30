## GlusterFS on AWS

This Quick Start deploys GlusterFS v7 on the Amazon Web Services (AWS) Cloud.

GlusterFS is a scalable network filesystem suitable for data-intensive tasks such as cloud storage and media streaming. GlusterFS is free and open source software and can utilize common off-the-shelf hardware. To learn more, please see [the Gluster project home page](https://www.gluster.org/).

Setup a GlusterFS cluster is not an easy task, by using this quick start which is based on AWS Cloud Development Kit (AWS CDK), a Gluster filesystem can be spinned up in AWS within minutes and clients can access the filesystem by native Gluster mount or NFSv3 mount.

> Notice ongoing maintenance of a GlusterFS cluster is your responsbility, like adding new bricks, replacing faulty bricks and managing snapshots, detail documentation can be found [here](https://docs.gluster.org/en/latest/Administrator%20Guide/overview/). This is why fully managed filesystem like [Amazon EFS](https://aws.amazon.com/efs/) is always preferred, however there are use cases that GlusterFS maybe more suitable, we will discuss this later.

The quick start implements the followings:

- Deploy Ubuntu 18.04 EC2s with EBS to selected VPC, they are the Gluster compute and storage nodes.
- Setup Gluster packages in every node, according to user selected Gluster volume type (distributed, dispersed or replicated)
- Deploy a Network Load Balancer (NLB) in front of this cluster, listening on Gluster control port: 24007.
- Deploy a security group of GlusterFS, client machines will be added to this security group to mount GlusterFS.

### Index

- [Use Cases](#use-cases)
- [Gluster Volume Type](#gluster-volume-type)
- [Usage](#usage)
  - [Prerequisites](#prerequisites)
  - [Deployment](#deployment)
  - [Mount GlusterFS by Native Client](#mount-glusterfs-by-native-client)
  - [Mount GlusterFS by NFSv3](#mount-glusterfs-by-nfsv3)
  - [Use GlusterFS in Kubernetes](#use-glusterfs-in-kubernetes)
  - [Access GlusterFS EC2 Nodes](#access-glusterfs-ec2-nodes)
- [Performance Testing](#performance-testing)
  - [Filesystem Benchmarks for 100kb Files](#filesystem-benchmarks-for-100kb-files)
  - [Filesystem Benchmarks for 1mb Files](#filesystem-benchmarks-for-1mb-files)
  - [Key Takeaway](#key-takeaway)
- [Cost of Running GlusterFS on AWS](#cost-of-running-glusterfs-on-aws)

### Use Cases

- **Cost effectiveness.** Provision a GlusterFS gives you the flexibility to choose the EC2 instance type, EBS volume type and no. of replica nodes, lower per GB/month price can be achieved if there is lower requriement on data durability/availability. Beware that EBS charges for provisioned storage while Amazon EFS only charges for actual storage used.
- **Performance.** By choosing appropriate EC2/EBS types and Gluster volume type, high performance and cost effective Gluster filesystem can be built. For fully managed high performance shared storage, remember to have a look at [Amazon FSx for Lustre](https://aws.amazon.com/fsx).
- **Service Availability.** In some AWS regions or in [AWS Outposts](https://aws.amazon.com/outposts/), where Amazon EFS not yet available, you can build a GlusterFS using EC2/EBS for shared file storage.
- **Lift and Shift GlusterFS to AWS.** If you are already using GlusterFS on-premises or in other clouds, and want the quickest way to migrate to AWS.

### Gluster Volume Type
A volume in Gluster is a logical collection of bricks where each brick is an export directory on a EC2/EBS in the trusted storage pool. Before creating a Gluster filesystem you should consider which volume type to create carefully:
- **Distributed** - Distributed volumes distribute files across the bricks in the volume. You can use distributed volumes where the requirement is to scale storage and the redundancy is either not important or is provided by other hardware/software layers.
- **Replicated** – Replicated volumes replicate files across bricks in the volume. You can use replicated volumes in environments where high-availability and high-reliability are critical.
- **Dispersed** - Dispersed volumes are based on erasure codes, providing space-efficient protection against disk or server failures. It stores an encoded fragment of the original file to each brick in a way that only a subset of the fragments is needed to recover the original file. The number of bricks that can be missing without losing access to data is configured by the administrator on volume creation time.

The above Gluster volume types are supported in this quick start. Gluster also has **Distributed Replicated** and **Distributed Dispersed** volume types, they are not supported in this quick start, please refer to [Gluster documentation](https://docs.gluster.org/en/latest/Administrator%20Guide/Setting%20Up%20Volumes/)

Here is a quick summary of different volume types:
| Volume Type | Brick (EC2) Count | Redundancy Count | Filesystem Size  | Max. no. of EC2s can be down |
|-------------|-------------------|------------------|------------------|------------------------------|
| Distributed | n                 | /                | EBS Size * n     | 0                            |
| Replicated  | n                 | /                | EBS Size         | n - 1                          |
| Dispersed   | n                 | k                | EBS Size * (n - k) | k                            |

> - Distributed volume has the best performance and bricks can be added very easily. But it has no redundancy at all, any EC2/EBS lost will cause data lost.
> - In theory replicated volume can lost up to n - 1 nodes, for example if n = 3 the filesystem should be able to operate normally with only 1 node. However you will find that the filesystem is down if node count < 2, this is because the default quorum = 2 in this case to avoid [Split Brain](https://docs.gluster.org/en/latest/Administrator%20Guide/Split%20brain%20and%20ways%20to%20deal%20with%20it/) issue.
> - Replicated volume can lost up to n - 1 nodes without data lost.
> - For dispersed volume, k must be an integer smaller than n/2, if you want k >= n/2, use replicated volume instead.
> - For best I/O performance of dispersed volume, make sure `(n - k) = power of 2`, for example n=3 and k=1, n=6 and k=2, etc.

### Usage

#### Prerequisites

To deploy the GlusterFS quick start, you will require an AWS account, prepare a VPC tagged with `glusterfsVpc=true` and private subnets which have outbound internet access only, GlusterFS will be automatically setup in the VPC and subnets.

Gluster related packages are downloaded and installed in the EC2s when they are spinning up, internet access can be turned off after GlusterFS is setup, to ensure the filesystem run in a private protected network.

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
10. Ensure an AWS VPC is tagged with `glusterfsVpc=true`, deploy GlusterFS to this VPC by:
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
The Gluster Native Client is a FUSE-based client running in user space. Gluster Native Client is **the recommended method** for accessing volumes when high concurrency and high write performance is required.
1. Ensure GlusterFS security group is added to your client EC2 instances.
2. Install GlusterFS client in client machines by `yum install glusterfs-client` (redhat/centos/fedora) or `apt install glusterfs-client` (ubuntu/debian)
3. Create a mount point directory at /mnt/gfs (or any name you preferred)
4. Open /etc/fstab, append the following line, replace GlusterNLBEndpoint with the actual endpoint:
    ```
    GlusterNLBEndpoint:/gfs /mnt/gfs glusterfs defaults,_netdev 0 0
    ```
5. Run command: `sudo mount -a`
6. Run command: `df`, you will see volume info of GlusterFS at /mnt/gfs mount point if things went well.

> Notice the GlusterNLBEndpoint only serves as a single endpoint for mounting and getting GlusterFS cluster info, the clients actually communicate with EC2s in the cluster directly when writing/reading files.

#### Mount GlusterFS by NFSv3
The quick start has setup a [NFS Ganesha server](https://docs.gluster.org/en/latest/Administrator%20Guide/NFS-Ganesha%20GlusterFS%20Integration/) and export GlusterFS via NFSv3 protocol, so every client machine can mount the GlusterFS with NFSv3.
Remember this is **NOT** the recommend way to mount GlusterFS, but if the clients can not install GlusterFS native client or you want to use service like [AWS DataSync](https://aws.amazon.com/datasync/), this is the only solution.

1. Ensure GlusterFS security group is added to your client EC2 instances.
2. Most Linux distributions have NFS client packages included, if not, simply install the `nfs-common` package.
3. Create a mount point directory at /mnt/gfsnfs (or any name you preferred)
4. Open /etc/fstab, append the following line, replace GlusterNFSEndpoint with the actual NFS endpoint:
    ```
    GlusterNFSEndpoint:/gfs /mnt/gfsnfs nfs defaults 0 0
    ```
5. Run command: `sudo mount -a`
6. Run command: `df`, you will see volume info of GlusterFS at /mnt/gfsnfs mount point if things went well.

#### Use GlusterFS in Kubernetes
There is a [Container Storage Interface (CSI) Driver for GlusterFS](https://kubernetes-csi.github.io/docs/drivers.html) can be used with Kubernetes, so that multiple k8s pods can read/write the shared and persistant Gluster filesystem. You can also use the CSI driver with [Amazon EKS](https://aws.amazon.com/eks/).

#### Access GlusterFS EC2 Nodes
The EC2s that form the GlusterFS are using Ubuntu 18.04 AMI with AWS Systems Manager Agent (SSM Agent) pre-installed, you can access them by [Session Manager](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/session-manager.html).

### Performance Testing
Basic filesystem performance benchmarks are collected using python script [smallfile](https://github.com/distributed-system-analysis/smallfile), the testing setup involves:
- RHEL v8.2 EC2 (t3.medium) as the testing client, all filesystems are mounted in this EC2.
- 8 threads are running to write/read files in the filesystems.
- The test is done for write/read 2048 files of 100kb per thread, and repeat for write/read 2048 files of 1mb.

#### Filesystem Benchmarks for 100kb Files
| Setup                                                             | EC2/EBS Type            | Write IOPS (100kb) | Read IOPS (100kb) | Write Throughput (mb/sec) | Read Throughput (mb/sec) |
|-------------------------------------------------------------------|-------------------------|--------------------|-------------------|---------------------------|--------------------------|
| Gluster **Distributed** Volume (3 bricks, native client)              | c5.large, 80gb gp2 EBS | 2092               | 2345              | 204                       | 229                      |
| Gluster **Distributed** Volume (3 bricks, native client)              | c5.large, 600gb st1 EBS | 2210               | 2342              | 215                       | 228                      |
| Gluster **Replicated** Volume (3 bricks, native client)               | c5.large, 80gb gp2 EBS | 855                | 1654              | 83                        | 161                      |
| Gluster **Dispersed** Volume (2 + 1 redundancy bricks, native client) | c5.large, 80gb gp2 EBS | 818                | 1036              | 79                        | 101                      |
| Gluster **Dispersed** Volume (4 + 2 redundancy bricks, native client) | c5.large, 80gb gp2 EBS | 564                | 757               | 55                        | 74                       |
| Gluster **Dispersed** Volume (2 + 1 redundancy bricks, nfs3 client)   | c5.large, 80gb gp2 EBS | 339                | 896               | 33                        | 87                       |
| Amazon EBS (gp2)                                                  | c5.large, 10gb gp2 EBS  | 3203               | 1423              | 312                       | 138                      |
| Amazon EFS (General purpose, bursting throughput)                 | /                       | 415                | 3337              | 40                        | 325                      |

#### Filesystem Benchmarks for 1mb Files
| Setup                                                             | EC2/EBS Type            | Write IOPS (1mb) | Read IOPS (1mb) | Write Throughput (mb/sec) | Read Throughput (mb/sec) |
|-------------------------------------------------------------------|-------------------------|------------------|-----------------|---------------------------|--------------------------|
| Gluster **Distributed** Volume (3 bricks, native client)              | c5.large, 80gb gp2 EBS  | 472              | 407             | 461                       | 398                      |
| Gluster **Distributed** Volume (3 bricks, native client)              | c5.large, 600gb st1 EBS | 517              | 408             | 505                       | 398                      |
| Gluster **Replicated** Volume (3 bricks, native client)               | c5.large, 80gb gp2 EBS  | 174              | 285             | 170                       | 278                      |
| Gluster **Dispersed** Volume (2 + 1 redundancy bricks, native client) | c5.large, 80gb gp2 EBS  | 269              | 281             | 263                       | 275                      |
| Gluster **Dispersed** Volume (4 + 2 redundancy bricks, native client) | c5.large, 80gb gp2 EBS  | 205              | 200             | 210                       | 205                      |
| Gluster **Dispersed** Volume (2 + 1 redundancy bricks, nfs3 client)   | c5.large, 80gb gp2 EBS  | 183              | 185             | 179                       | 181                      |
| Amazon EBS (gp2)                                                  | c5.large, 80gb gp2 EBS  | 139              | 132             | 135                       | 129                      |
| Amazon EFS (General purpose, bursting throughput)                 | /                       | 103              | 104             | 101                       | 101                      |

#### Key Takeaway
- GlusterFS has better write performance than Amazon EFS for both 100kb and 1mb files.
- While Amazon EFS has better read performance than GlusterFS for 100kb files.
- Gluster **Distributed** volume type has the best performance.

> Notice that EBS (gp2) has a baseline IOPS of `3 * volume size` which can be bursted to 3,000 IOPS for an extended period of time ([detail](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ebs-volume-types.html)). To achieve consistent IOPS performance, you can provision EBS of over 1TB in size.

### Cost of Running GlusterFS on AWS
- ***EC2***, number of EC2s = number of bricks
- ***EBS***, number of EBS volumes = number of bricks
- ***Data transfer cost between AZs***, for the best resiliancy this quick start deploys GlusterFS nodes into different AZs, data egress from one AZ to another incurs a charge.
