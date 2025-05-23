package uk.gov.justice.digital.hmpps.hmppsintegrationapi.gateways.nomis

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.mockito.internal.verification.VerificationModeFactory
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.gateways.PrisonApiGateway
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.mockservers.ApiMockServer
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.mockservers.HmppsAuthMockServer
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.UpstreamApi
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.UpstreamApiError
import java.time.LocalDate

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [PrisonApiGateway::class],
)
class GetReasonableAdjustmentTest(
  @MockitoBean val hmppsAuthGateway: HmppsAuthGateway,
  private val prisonApiGateway: PrisonApiGateway,
) : DescribeSpec(
    {
      val nomisApiMockServer = ApiMockServer.create(UpstreamApi.PRISON_API)
      val bookingId = "mockBooking"
      val domainPath = "/api/reference-domains/domains/HEALTH_TREAT/codes"
      var reasonableAdjustmentPath = "/api/bookings/$bookingId/reasonable-adjustments?type=a&type=b&type=c"
      beforeEach {
        nomisApiMockServer.start()
        nomisApiMockServer.stubForGet(
          domainPath,
          """
          [
            {"domain":"abc", "code":"a"},
            {"domain":"abc", "code":"b"},
            {"domain":"abc", "code":"c"}
          ]
        """,
        )

        nomisApiMockServer.stubForGet(
          reasonableAdjustmentPath,
          """
            { "reasonableAdjustments":[
                {
                      "treatmentCode": "WHEELCHR_ACC",
                      "commentText": "abcd",
                      "startDate": "2010-06-21",
                      "endDate": "2010-06-21",
                      "treatmentDescription": "Wheelchair accessibility"
                 }
              ]
           }
        """,
        )

        Mockito.reset(hmppsAuthGateway)
        whenever(hmppsAuthGateway.getClientToken("NOMIS")).thenReturn(HmppsAuthMockServer.TOKEN)
      }

      afterTest {
        nomisApiMockServer.stop()
      }

      it("authenticates using HMPPS Auth with credentials") {
        prisonApiGateway.getReasonableAdjustments(bookingId)

        verify(hmppsAuthGateway, VerificationModeFactory.times(2)).getClientToken("NOMIS")
      }

      it("returns reasonable adjustment for a person with the matching ID") {
        val response = prisonApiGateway.getReasonableAdjustments(bookingId)

        response.data.count().shouldBe(1)
        response.data
          .first()
          .treatmentCode
          .shouldBe("WHEELCHR_ACC")
        response.data
          .first()
          .commentText
          .shouldBe("abcd")
        response.data
          .first()
          .startDate
          .shouldBe(LocalDate.parse("2010-06-21"))
        response.data
          .first()
          .endDate
          .shouldBe(LocalDate.parse("2010-06-21"))
        response.data
          .first()
          .treatmentDescription
          .shouldBe("Wheelchair accessibility")
      }

      it("returns an empty list when no reasonable adjustment are found") {
        nomisApiMockServer.stubForGet(domainPath, "[]")

        val response = prisonApiGateway.getReasonableAdjustments(bookingId)

        response.data.shouldBeEmpty()
      }

      it("returns an error when 404 NOT FOUND is returned") {
        nomisApiMockServer.stubForGet(
          domainPath,
          """
        {
          "developerMessage": "cannot find person"
        }
        """,
          HttpStatus.NOT_FOUND,
        )

        val response = prisonApiGateway.getReasonableAdjustments(bookingId)

        response.hasError(UpstreamApiError.Type.ENTITY_NOT_FOUND).shouldBeTrue()
      }
    },
  )
