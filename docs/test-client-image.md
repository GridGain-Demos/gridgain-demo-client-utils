# Test-Client Docker Image

The plugin's `connectTestClient` task and the Job manifest emitted by
`deployCluster` use a built-in test-client image published by GridGain at:

- `ghcr.io/gridgain-demos/demo-test-client-gg9:<plugin-version>`
- `ghcr.io/gridgain-demos/demo-test-client-gg8:<plugin-version>`

These images are public on GHCR. **Most users never need to build them** —
they're pulled automatically by your cluster when the Job manifest is applied.

This page covers:

- [End users](#end-users): the typical workflow (no build step)
- [Override paths](#override-paths) for air-gapped clusters or local forks
- [GridGain maintainers](#gridgain-maintainers): how the images are published

---

## End users

When you deploy a cluster, the plugin generates a Job manifest at
`<demoOutputDirectory>/test-client/test-client-<cluster>-job.yaml`. Apply it:

```bash
kubectl apply -f build/gridgain/output/test-client/test-client-<cluster>-job.yaml
kubectl logs -f job/test-client-<cluster>
```

The Job pulls `ghcr.io/gridgain-demos/demo-test-client-gg{8,9}:<plugin-version>`
automatically. No Docker daemon needed on your laptop, no registry account
required, no build step.

**Expected output:**

```
=== TestClientV9 ===
Cluster name : trip-cluster
Runtime ctx  : IN_CLUSTER
Resolved addresses (3):
  trip-cluster-0.trip-cluster.taxi-demo.svc.cluster.local:10800
  trip-cluster-1.trip-cluster.taxi-demo.svc.cluster.local:10800
  trip-cluster-2.trip-cluster.taxi-demo.svc.cluster.local:10800

=== Per-node reachability ===
  trip-cluster-0.trip-cluster.taxi-demo.svc.cluster.local:10800 ... OK
  trip-cluster-1.trip-cluster.taxi-demo.svc.cluster.local:10800 ... OK
  trip-cluster-2.trip-cluster.taxi-demo.svc.cluster.local:10800 ... OK

Reachable: 3/3 server nodes.
All server nodes reachable.
```

---

## Override paths

You only need to build your own image in these cases:

1. **Air-gapped or restricted-egress clusters** that can't reach `ghcr.io`.
2. **Corporate proxy / private registry** policies that require all images
   to come from an internal registry.
3. **Local fork** of the plugin or client-utils that you're testing before
   release.

Build and push to your registry, then re-emit the Job manifest with the
matching `image:` field:

```bash
cd gridgain-demo-client-utils
./gradlew :gg9-test-client-image:jib -PimageRegistry=<your-registry>

cd ../<your-demo-project>
./gradlew deployCluster -PelementName=<cluster> -PimageRegistry=<your-registry>
# ↑ Re-emits the Job manifest with the registry-prefixed image name.

kubectl apply -f build/gridgain/output/test-client/test-client-<cluster>-job.yaml
```

`<your-registry>` is whatever path your registry expects:

| Registry | Example |
| --- | --- |
| Docker Hub | `docker.io/myuser` (or just `myuser`) |
| GitHub Container Registry | `ghcr.io/<user-or-org>` |
| AWS ECR | `<account>.dkr.ecr.<region>.amazonaws.com/<repo>` |
| Google Artifact Registry | `<region>-docker.pkg.dev/<project>/<repo>` |
| Azure Container Registry | `<name>.azurecr.io/<repo>` |
| Self-hosted | `registry.mycorp.com/<path>` |

If you need a non-default tag, add `-PimageTag=<your-tag>` to both commands.

### Local k8s shortcut (kind / minikube / k3d)

If you're iterating on a local fork against a local cluster, you can skip the
registry entirely:

```bash
cd gridgain-demo-client-utils
./gradlew :gg9-test-client-image:jibDockerBuild -PimageRegistry=local
kind load docker-image local/demo-test-client-gg9:0.0.5-SNAPSHOT --name <cluster>
# minikube image load local/demo-test-client-gg9:0.0.5-SNAPSHOT
# k3d image import local/demo-test-client-gg9:0.0.5-SNAPSHOT -c <cluster>

cd ../<your-demo-project>
./gradlew deployCluster -PelementName=<cluster> -PimageRegistry=local
kubectl apply -f build/gridgain/output/test-client/test-client-<cluster>-job.yaml
```

### Air-gapped: OCI tarball

Produce an OCI tarball you can ship via any channel:

```bash
./gradlew :gg9-test-client-image:jibBuildTar -PimageRegistry=local
# Output: gg9-test-client-image/build/jib-image.tar
```

Load on the target with `docker load -i jib-image.tar`, retag for your
internal registry, then push.

### Override authentication

Jib reads credentials from the standard Docker config
(`~/.docker/config.json` populated by `docker login`) and from the standard
cloud auth helpers (gcloud, aws-cli, az). If you can `docker push` to your
registry, Jib can push to it. See the
[Jib auth docs](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#how-do-i-configure-credentials)
for the precedence order.

---

## Customizing the image

The Jib config lives in `gg{8,9}-test-client-image/build.gradle.kts`. Common
edits:

- **Base image**: change `from { image = "eclipse-temurin:17-jre" }` to a
  different JRE (e.g. `gcr.io/distroless/java17-debian12` for a smaller
  attack surface).
- **Labels**: extend the `container.labels` map.
- **JVM flags**: add `container.jvmFlags = listOf("-Xmx256m")`.

See the [Jib Gradle plugin docs](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin)
for the full configuration surface.

---

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `kubectl apply` reports `ErrImagePull` from `ghcr.io/gridgain-demos/...` | Cluster cannot reach `ghcr.io`, or the package is private (transient state during a release) | Verify your cluster has internet egress; if persistent, see [Override paths](#override-paths) to mirror the image internally |
| Job runs but `kubectl logs` shows `MissingEndpointsFileException` | The `client-endpoints` ConfigMap is not mounted, or its entry for this cluster is stale | Re-run `deployCluster` to refresh the ConfigMap; verify with `kubectl get configmap gridgain-demo-client-endpoints` |
| Job exits 1 with `Some server nodes unreachable` | Network policies, headless-service config, or stale in-cluster DNS | Inspect `kubectl get pods,svc -n <cluster-namespace>`; verify the headless service exposes the client port (10800) |
| `jibDockerBuild` fails with "Build to Docker daemon failed" | Docker daemon not running, or socket permissions wrong | Start Docker Desktop; verify `docker ps` works |
| `jib` push fails with 401/403 | Registry credentials missing or expired | `docker login <registry>` (or the equivalent cloud auth helper) and retry |

---

## GridGain maintainers

The built-in images are pushed by `bump-version.sh --publish`. The release
flow is:

1. **Bump version**: `./bin/bump-version.sh 0.0.6-SNAPSHOT --commit`
2. **Publish**: `./bin/bump-version.sh 0.0.6-SNAPSHOT --publish` — this
   pushes the test-client images to GHCR FIRST, then the JAR artifacts to
   Nexus. Image push runs first so plugin manifests never reference a
   non-existent image tag.

### One-time setup

GHCR push needs a GitHub PAT (Personal Access Token) with `write:packages`
scope. Add to `~/.gradle/gradle.properties`:

```properties
gridgainGhcrUsername=<your-github-login>
gridgainGhcrPassword=<github-pat-with-write:packages>
```

The `gridgain-demos` org's package settings determine visibility. After the
**first push** of each image (gg8 + gg9), an org admin must mark the package
public:

1. Go to https://github.com/orgs/gridgain-demos/packages
2. Find `demo-test-client-gg8` and `demo-test-client-gg9`
3. **Package settings** → **Change visibility** → Public
4. While there, link each package to
   https://github.com/GridGain-Demos/gridgain-client-utils so the package
   page shows the source repo.

This is one-time per package. Subsequent pushes inherit the visibility.

### Skipping image push

`./bin/bump-version.sh <version> --publish --skip-images` publishes JARs
only. Useful for republishing a SNAPSHOT after a JAR-only bug fix without
bothering Docker.

### Manual image rebuild

If you need to rebuild a specific image outside the release flow:

```bash
cd gridgain-demo-client-utils
./gradlew :gg9-test-client-image:jib       # push current version to GHCR
./gradlew :gg9-test-client-image:jibBuildTar  # local tarball, no auth needed
```
