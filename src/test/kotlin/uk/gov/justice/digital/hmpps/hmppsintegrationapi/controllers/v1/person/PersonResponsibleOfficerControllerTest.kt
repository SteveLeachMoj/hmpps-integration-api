package uk.gov.justice.digital.hmpps.hmppsintegrationapi.controllers.v1.person

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.mockito.Mockito
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.extensions.removeWhitespaceAndNewlines
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.helpers.IntegrationAPIMockMvc
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.CommunityOffenderManager
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.PersonResponsibleOfficerName
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.PersonResponsibleOfficerTeam
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.Prison
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.PrisonOffenderManager
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.Response
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.UpstreamApi
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.models.hmpps.UpstreamApiError
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.services.GetCommunityOffenderManagerForPersonService
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.services.GetPrisonOffenderManagerForPersonService
import uk.gov.justice.digital.hmpps.hmppsintegrationapi.services.internal.AuditService

@WebMvcTest(controllers = [PersonResponsibleOfficerController::class])
@ActiveProfiles("test")
internal class PersonResponsibleOfficerControllerTest(
  @Autowired var springMockMvc: MockMvc,
  @MockitoBean val auditService: AuditService,
  @MockitoBean val getCommunityOffenderManagerForPersonService: GetCommunityOffenderManagerForPersonService,
  @MockitoBean val getPrisonOffenderManagerForPersonService: GetPrisonOffenderManagerForPersonService,
) : DescribeSpec() {
  init {
    val hmppsId = "11111A"
    val path = "/v1/persons/$hmppsId/person-responsible-officer"
    val mockMvc = IntegrationAPIMockMvc(springMockMvc)
    val filters = null

    describe("GET $path") {
      beforeTest {
        Mockito.reset(getPrisonOffenderManagerForPersonService)
        Mockito.reset(getCommunityOffenderManagerForPersonService)
        whenever(getPrisonOffenderManagerForPersonService.execute(hmppsId, filters)).thenReturn(
          Response(
            PrisonOffenderManager(
              forename = "Paul",
              surname = "Reds",
              prison = Prison(code = "PrisonCode1"),
            ),
          ),
        )

        whenever(getCommunityOffenderManagerForPersonService.execute(hmppsId, filters)).thenReturn(
          Response(
            CommunityOffenderManager(
              name = PersonResponsibleOfficerName("Helen", surname = "Miller"),
              email = "helenemail@email.com",
              telephoneNumber = "0987654321",
              team =
                PersonResponsibleOfficerTeam(
                  code = "PrisonCode2",
                  description = "Description",
                  email = "email_again@email.com",
                  telephoneNumber = "01234567890",
                ),
            ),
          ),
        )
        Mockito.reset(auditService)
      }

      it("returns a 200 OK status code") {
        val result = mockMvc.performAuthorised(path)

        result.response.status.shouldBe(HttpStatus.OK.value())
      }

      it("gets the person responsible officer for a person with the matching ID") {
        mockMvc.performAuthorised(path)
        verify(getCommunityOffenderManagerForPersonService, times(1)).execute(hmppsId, filters)
      }

      it("logs audit") {
        mockMvc.performAuthorised(path)

        verify(
          auditService,
          times(1),
        ).createEvent("GET_PERSON_RESPONSIBLE_OFFICER", mapOf("hmppsId" to hmppsId))
      }

      it("returns the person responsible officer for a person with the matching ID") {
        val result = mockMvc.performAuthorised(path)

        result.response.contentAsString.shouldContain(
          """
            "data": {
                "prisonOffenderManager": {
                    "forename": "Paul",
                    "surname": "Reds",
                    "prison": {
                        "code": "PrisonCode1"
                    }
                },
                "communityOffenderManager": {
                    "name": {
                        "forename": "Helen",
                        "surname": "Miller"
                    },
                    "email": "helenemail@email.com",
                    "telephoneNumber": "0987654321",
                    "team": {
                        "code": "PrisonCode2",
                        "description": "Description",
                        "email": "email_again@email.com",
                        "telephoneNumber": "01234567890"
                    }
                }
            }
            """.removeWhitespaceAndNewlines(),
        )
      }

      it("returns a 400 when getPrisonOffenderManagerForPersonService returns a bad request") {
        whenever(getPrisonOffenderManagerForPersonService.execute(hmppsId, filters)).thenReturn(
          Response(
            data = null,
            errors = listOf(UpstreamApiError(UpstreamApi.MANAGE_POM_CASE, UpstreamApiError.Type.BAD_REQUEST)),
          ),
        )

        val result = mockMvc.performAuthorised(path)
        result.response.status.shouldBe(HttpStatus.BAD_REQUEST.value())
      }

      it("returns a 404 when getPrisonOffenderManagerForPersonService returns a not found") {
        whenever(getPrisonOffenderManagerForPersonService.execute(hmppsId, filters)).thenReturn(
          Response(
            data = null,
            errors = listOf(UpstreamApiError(UpstreamApi.MANAGE_POM_CASE, UpstreamApiError.Type.ENTITY_NOT_FOUND)),
          ),
        )

        val result = mockMvc.performAuthorised(path)
        result.response.status.shouldBe(HttpStatus.NOT_FOUND.value())
      }

      it("returns a 400 when getCommunityOffenderManagerForPersonService returns a bad request") {
        whenever(getCommunityOffenderManagerForPersonService.execute(hmppsId, filters)).thenReturn(
          Response(
            data = null,
            errors = listOf(UpstreamApiError(UpstreamApi.NDELIUS, UpstreamApiError.Type.BAD_REQUEST)),
          ),
        )

        val result = mockMvc.performAuthorised(path)
        result.response.status.shouldBe(HttpStatus.BAD_REQUEST.value())
      }

      it("returns a 404 when getCommunityOffenderManagerForPersonService returns a not found") {
        whenever(getCommunityOffenderManagerForPersonService.execute(hmppsId, filters)).thenReturn(
          Response(
            data = null,
            errors = listOf(UpstreamApiError(UpstreamApi.NDELIUS, UpstreamApiError.Type.ENTITY_NOT_FOUND)),
          ),
        )

        val result = mockMvc.performAuthorised(path)
        result.response.status.shouldBe(HttpStatus.NOT_FOUND.value())
      }
    }
  }
}
