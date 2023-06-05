package dev.restate.e2e.runtime

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.restate.e2e.Containers
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.utils.InjectGrpcIngressURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Test the Connect ingress support */
class ConnectIngressTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .build())

    private val objMapper = ObjectMapper()

    private val jacksonBodySubscriber: HttpResponse.BodySubscriber<JsonNode> =
        HttpResponse.BodySubscribers.mapping(
            HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), objMapper::readTree)

    private val jacksonBodyHandler: HttpResponse.BodyHandler<JsonNode> =
        HttpResponse.BodyHandler { jacksonBodySubscriber }

    private fun jacksonBodyPublisher(value: Any): HttpRequest.BodyPublisher {
      return BodyPublishers.ofString(objMapper.writeValueAsString(value))
    }
  }

  @Test
  fun getAndAdd(@InjectGrpcIngressURL httpEndpointURL: URL) {
    val client = HttpClient.newHttpClient()

    val req =
        HttpRequest.newBuilder(
                URI.create("$httpEndpointURL${CounterGrpc.getGetAndAddMethod().fullMethodName}"))
            .POST(jacksonBodyPublisher(mapOf("counter_name" to "my-counter", "value" to 1)))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, jacksonBodyHandler)

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.headers().firstValue("content-type"))
        .get()
        .asString()
        .contains("application/json")
    assertThat(response.body().get("newValue").asInt()).isEqualTo(1)
  }

  @Test
  fun badContentType(@InjectGrpcIngressURL httpEndpointURL: URL) {
    val client = HttpClient.newHttpClient()

    val req =
        HttpRequest.newBuilder(
                URI.create("$httpEndpointURL${CounterGrpc.getGetAndAddMethod().fullMethodName}"))
            .POST(jacksonBodyPublisher(mapOf("counter_name" to "my-counter", "value" to 1)))
            .headers("Content-Type", "application/whatever")
            .build()

    val response = client.send(req, jacksonBodyHandler)

    assertThat(response.statusCode()).isEqualTo(415)
  }

  @Test
  fun malformedJson(@InjectGrpcIngressURL httpEndpointURL: URL) {
    val client = HttpClient.newHttpClient()

    val req =
        HttpRequest.newBuilder(
                URI.create("$httpEndpointURL${CounterGrpc.getGetAndAddMethod().fullMethodName}"))
            .POST(BodyPublishers.ofString("{"))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, jacksonBodyHandler)

    assertThat(response.statusCode()).isEqualTo(400)
  }
}
