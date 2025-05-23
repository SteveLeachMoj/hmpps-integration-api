package uk.gov.justice.digital.hmpps.hmppsintegrationapi.gateways

import org.apache.tomcat.util.json.JSONParser
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.exception.HmppsAuthFailedException
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.Credentials

@Component
class HmppsAuthGateway(
  @Value("\${services.hmpps-auth.base-url}") hmppsAuthUrl: String,
) : IAuthGateway {
  private val webClient: WebClient = WebClient.builder().baseUrl(hmppsAuthUrl).build()

  @Value("\${services.hmpps-auth.username}")
  private lateinit var username: String

  @Value("\${services.hmpps-auth.password}")
  private lateinit var password: String

  override fun getClientToken(service: String): String {
    val credentials = Credentials(username, password)

    return try {
      val response =
        webClient
          .post()
          .uri("/auth/oauth/token?grant_type=client_credentials")
          .header("Authorization", credentials.toBasicAuth())
          .retrieve()
          .bodyToMono(String::class.java)
          .block()

      JSONParser(response).parseObject()["access_token"].toString()
    } catch (exception: WebClientRequestException) {
      throw HmppsAuthFailedException("Connection to ${exception.uri.authority} failed for $service.")
    } catch (exception: WebClientResponseException.ServiceUnavailable) {
      throw HmppsAuthFailedException("${exception.request?.uri?.authority} is unavailable for $service.")
    } catch (exception: WebClientResponseException.Unauthorized) {
      throw HmppsAuthFailedException("Invalid credentials used for $service.")
    }
  }
}
