package scaladex.server.route

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import scaladex.core.model.Env
import scaladex.core.model.UserState
import scaladex.core.model.search.Page
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.SearchParams
import scaladex.core.model.search.Sorting
import scaladex.core.service.SearchEngine
import scaladex.server.TwirlSupport._
import scaladex.view.search.html.searchresult

class SearchPages(env: Env, searchEngine: SearchEngine)(
    implicit ec: ExecutionContext
) {
  def route(user: Option[UserState]): Route =
    get(
      concat(
        path("search")(
          searchParams(user)(params => paging(size = 20)(page => search(params, page, user, "search")))
        ),
        path(Segment)(organization =>
          searchParams(user) { params =>
            val paramsWithOrg = params.copy(queryString = s"${params.queryString} AND organization:$organization")
            paging(size = 20)(page => search(paramsWithOrg, page, user, s"organization/$organization"))
          }
        )
      )
    )

  private def searchParams(user: Option[UserState]): Directive1[SearchParams] =
    parameters(
      "q" ? "*",
      "sort".?,
      "topics".as[String].*,
      "languages".as[String].*,
      "platforms".as[String].*,
      "you".?,
      "contributingSearch".as[Boolean] ? false
    ).tmap {
      case (q, sortParam, topics, languages, platforms, you, contributingSearch) =>
        val userRepos = you.flatMap(_ => user.map(_.repos)).getOrElse(Set())
        val sorting = sortParam.flatMap(Sorting.byLabel.get).getOrElse(Sorting.Stars)
        SearchParams(
          q,
          sorting,
          userRepos,
          topics = topics.toSeq,
          languages = languages.toSeq,
          platforms = platforms.toSeq,
          contributingSearch = contributingSearch
        )
    }

  private def search(params: SearchParams, page: PageParams, user: Option[UserState], uri: String) =
    complete {
      val resultsF = searchEngine.find(params, page)
      val topicsF = searchEngine.countByTopics(params, 50)
      val platformsF = searchEngine.countByPlatforms(params)
      val languagesF = searchEngine.countByLanguages(params)

      for {
        Page(pagination, projects) <- resultsF
        topics <- topicsF
        languages <- languagesF
        platforms <- platformsF
      } yield searchresult(
        env,
        params,
        uri,
        pagination,
        projects,
        user,
        params.userRepos.nonEmpty,
        topics,
        languages,
        platforms
      )
    }
}
