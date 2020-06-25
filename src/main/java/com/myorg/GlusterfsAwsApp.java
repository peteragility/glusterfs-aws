package com.myorg;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public final class GlusterfsAwsApp {
    public static void main(final String[] args) {
        App app = new App();

        StackProps envProps = StackProps.builder().env(Environment.builder().account("693858346231").region("ap-east-1").build()).build();
        new GlusterfsAwsStack(app, "GlusterfsAwsStack", envProps);

        app.synth();
    }
}
