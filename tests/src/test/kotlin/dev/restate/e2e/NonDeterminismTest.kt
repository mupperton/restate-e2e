package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.functions.counter.CounterProto
import dev.restate.e2e.functions.nondeterminism.NonDeterminismProto.NonDeterministicRequest
import dev.restate.e2e.functions.nondeterminism.NonDeterministicServiceGrpc
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.FunctionSpec.*
import io.grpc.*
import io.grpc.stub.ClientCalls
import java.util.*
import java.util.stream.Stream
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@Tag("only-always-suspending")
class JavaNonDeterminismTest : NonDeterminismTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                            "java-non-determinism",
                            NonDeterministicServiceGrpc.SERVICE_NAME,
                            CounterGrpc.SERVICE_NAME)
                        .withRegistrationOptions(
                            RegistrationOptions(retryPolicy = RetryPolicy.None)))
                .build())
  }
}

@Tag("only-always-suspending")
class NodeNonDeterminismTest : NonDeterminismTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                            "node-non-determinism",
                            NonDeterministicServiceGrpc.SERVICE_NAME,
                            CounterGrpc.SERVICE_NAME)
                        .withRegistrationOptions(
                            RegistrationOptions(retryPolicy = RetryPolicy.None)))
                .build())
  }
}

abstract class NonDeterminismTest {
  companion object {
    @JvmStatic
    fun method(): Stream<Arguments> {
      return NonDeterministicServiceGrpc.getServiceDescriptor().methods.stream().map {
        Arguments.of(it.bareMethodName, it)
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  fun method(
      methodName: String,
      methodDescriptor: MethodDescriptor<NonDeterministicRequest, Empty>,
      @InjectChannel channel: ManagedChannel,
      @InjectBlockingStub counterService: CounterBlockingStub
  ) {
    val counterName = UUID.randomUUID().toString()

    Assertions.assertThatThrownBy {
          ClientCalls.blockingUnaryCall(
              channel,
              methodDescriptor,
              CallOptions.DEFAULT,
              NonDeterministicRequest.newBuilder().setKey(counterName).build())
        }
        .asInstanceOf(InstanceOfAssertFactories.type(StatusRuntimeException::class.java))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getCode)
        .isEqualTo(Status.Code.INTERNAL)

    // Assert the counter was not incremented
    assertThat(
            counterService
                .get(CounterProto.CounterRequest.newBuilder().setCounterName(methodName).build())
                .value)
        .isZero()
  }
}