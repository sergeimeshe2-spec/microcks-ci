import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

version = "2024.12"

object Project : Project({
    id = RelativeId("MicrocksCustom")
    name = "Microcks Custom"
    description = "Microcks custom build with CI/CD"

    // VCS Root - Main repository
    val githubVcs = GitVcsRoot {
        id = RelativeId("MicrocksGithub")
        name = "microcks-custom-github"
        url = "https://github.com/sergeimeshe2-spec/microcks-custom.git"
        branch = "refs/heads/main"
        branchSpec = """
            +:refs/heads/*
            +:refs/pull/(*)/head
        """.trimIndent()
        authMethod = password {
            userName = "sergeimeshe2-spec"
            password = "credentialsJSON:GITHUB_TOKEN"
        }
        useTagsAsBranches = true
        agentCleanFilesPolicy = AgentCleanFilesPolicy.ALL_UNTRACKED
    }

    // Build Configuration: Build
    val build = BuildType {
        id = AbsoluteId("MicrocksCustom_Build")
        name = "Build"
        description = "Build Microcks with Maven"

        vcs {
            root(githubVcs, "+:.")
        }

        steps {
            script {
                name = "Maven Build"
                scriptContent = """
                    echo "Building from branch: %branch%"
                    mvn clean install -DskipTests -DskipITs
                """.trimIndent()
            }
        }

        params {
            param("env.JAVA_HOME", "%env.JDK_21%")
            // Branch selection parameter
            param("branch", "refs/heads/main")
        }

        triggers {
            vcs {
                branchFilter = "+:*"
            }
        }

        requirements {
            contains("docker", "true")
        }

        artifactRules = """
            webapp/target/*.jar => artifacts/
            webapp/target/lib => artifacts/lib
        """.trimIndent()
    }

    // Build Configuration: Docker
    val docker = BuildType {
        id = AbsoluteId("MicrocksCustom_Docker")
        name = "Docker"
        description = "Build and push Docker image"

        type = BuildTypeSettings.Type.DEPLOYMENT

        vcs {
            root(githubVcs, "+:.")
        }

        dependencies {
            dependency(build) {
                artifacts {
                    artifactRules = "artifacts/** => webapp/target/"
                }
                // Depend on same branch
                onDependencyFailure = FailureAction.CANCEL
            }
        }

        params {
            param("branch", "%dep.MicrocksCustom_Build.branch%")
        }

        steps {
            script {
                name = "Set Version"
                scriptContent = """
                    echo "##teamcity[setParameter name='IMAGE_TAG' value='%build.number%']"
                """.trimIndent()
            }

            script {
                name = "Build Docker Image"
                scriptContent = """
                    cd webapp
                    docker build -t ghcr.io/sergeimeshe2-spec/microcks-custom:%IMAGE_TAG% -t ghcr.io/sergeimeshe2-spec/microcks-custom:latest src/main/docker
                """.trimIndent()
            }

            script {
                name = "Push Docker Image"
                scriptContent = """
                    echo %DockerRegistryPassword% | docker login ghcr.io -u sergeimeshe2-spec --password-stdin
                    docker push ghcr.io/sergeimeshe2-spec/microcks-custom:%IMAGE_TAG%
                    docker push ghcr.io/sergeimeshe2-spec/microcks-custom:latest
                    docker logout ghcr.io
                """.trimIndent()
            }
        }

        params {
            password("DockerRegistryPassword", "credential:JSON", display = PasswordDisplay.HIDDEN)
        }

        triggers {
            finishBuildTrigger {
                buildType = build
                successfulOnly = true
                branchFilter = "+:*"
            }
        }
    }

    // Build Configuration: Deploy
    val deploy = BuildType {
        id = AbsoluteId("MicrocksCustom_Deploy")
        name = "Deploy"
        description = "Deploy to Kubernetes with Helm"

        type = BuildTypeSettings.Type.DEPLOYMENT

        vcs {
            root(githubVcs, "+:.")
        }

        dependencies {
            dependency(docker) {
                snapshotOnDependencyFailure = false
            }
        }

        params {
            param("branch", "%dep.MicrocksCustom_Docker.branch%")
        }

        steps {
            script {
                name = "Clone Helm Repository"
                scriptContent = """
                    rm -rf helm-charts
                    git clone https://github.com/sergeimeshe2-spec/microcks-helm.git helm-charts
                """.trimIndent()
            }

            script {
                name = "Helm Deploy"
                scriptContent = """
                    helm upgrade --install microcks helm-charts/ \
                      --namespace microcks \
                      --create-namespace \
                      --wait \
                      --timeout 5m \
                      --set image.registry=ghcr.io \
                      --set image.repository=sergeimeshe2-spec/microcks-custom \
                      --set image.tag=%dep.MicrocksCustom_Docker.build.number%
                """.trimIndent()
            }

            script {
                name = "Smoke Tests"
                scriptContent = """
                    kubectl get pods -n microcks
                    kubectl rollout status deployment/microcks -n microcks --timeout=3m
                    echo "✅ Deploy successful!"
                """.trimIndent()
            }
        }

        enablePersonalBuild = false
    }

    buildTypesOrder = arrayListOf(
        build,
        docker,
        deploy
    )

    params {
        param("env.JDK_21", "/opt/java/openjdk")
        param("GITHUB_TOKEN", "credentialsJSON:GITHUB_TOKEN")
    }
})
