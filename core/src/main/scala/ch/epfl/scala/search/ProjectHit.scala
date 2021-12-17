package ch.epfl.scala.search

import ch.epfl.scala.index.model.misc.GithubIssue

/**
  * found project with issues hit by search engine
  */
final case class ProjectHit(
    document: ProjectDocument,
    beginnerIssueHits: Seq[GithubIssue]
) {
  def displayedIssues: Seq[GithubIssue] =
    if (beginnerIssueHits.nonEmpty) beginnerIssueHits
    else document.githubInfo.toSeq.flatMap(_.beginnerIssues)
}
