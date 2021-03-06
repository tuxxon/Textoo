package org.bluecabin.textoo.impl

import java.util.regex.Pattern

import android.text.Spanned
import android.text.util.Linkify
import android.text.util.Linkify.{MatchFilter, TransformFilter}
import org.bluecabin.textoo.impl.Change.ChangeQueue
import org.bluecabin.textoo.util.CharSequenceSupport._
import org.bluecabin.textoo.{Configurator, TextLinkify}

import scala.collection.immutable.Queue

/**
  * Created by fergus on 1/6/16.
  */
private trait TextLinkifyImpl[T, C <: Configurator[T] with TextLinkify[T, C]]
  extends ConfiguratorImpl[T, C] with TextLinkify[T, C] {

  override final def linkifyPhoneNumbers(): C = addAutoLinkMask(Linkify.PHONE_NUMBERS)

  override final def linkifyEmailAddresses(): C = addAutoLinkMask(Linkify.EMAIL_ADDRESSES)

  override final def linkifyAll(): C = addAutoLinkMask(Linkify.ALL)

  override final def linkifyMapAddresses(): C = addAutoLinkMask(Linkify.MAP_ADDRESSES)

  override final def linkifyWebUrls(): C = addAutoLinkMask(Linkify.WEB_URLS)

  override final def linkify(pattern: Pattern, scheme: String): C = addChange(new Change[T] {
    override def apply(text: T): T = preserveExistingLinks(text)(linkifyText(_, pattern, scheme))
  })

  override final def linkify(p: Pattern, scheme: String, matchFilter: MatchFilter, transformFilter: TransformFilter): C =
    addChange(new Change[T] {
      override def apply(text: T): T = preserveExistingLinks(text)(linkifyText(_, p, scheme, matchFilter, transformFilter))
    })

  private case class AutoLink(val mask: Int) extends Change[T] {

    private def merge(pending: Seq[Change[T]], newMask: Int, done: ChangeQueue[T]): ChangeQueue[T] = {
      pending.headOption match {
        case Some(AutoLink(mask)) => merge(pending.tail, newMask | mask, done)
        case Some(change) => merge(pending.tail, newMask, done.enqueue(change))
        case None => done.enqueue(AutoLink(newMask))
      }
    }

    override def addTo(changes: ChangeQueue[T]): ChangeQueue[T] = merge(Seq(changes: _*), mask, Queue.empty)

    override def apply(text: T): T = preserveExistingLinks(text)(linkifyText(_, mask))
  }

  private def addAutoLinkMask(mask: Int): C = addChange(AutoLink(mask))

  private def preserveExistingLinks(text: T)(linkify: T => T): T = {
    val preexistingLinks = getSpanned(text).allLinks
    val linkifiedText = linkify(text)
    val linkifiedSpanned = getSpanned(linkifiedText)
    val linkifiedLinks = linkifiedSpanned.allLinks
    def findOverlappingLinks(oldLinks: Seq[SpanInfo], newLinks: Seq[SpanInfo], overlappingLinks: Seq[SpanInfo] = Seq.empty): Seq[SpanInfo] = {
      newLinks.headOption match {
        case Some(newLink) => if (oldLinks.exists(_.overlapsWith(newLink))) {
          findOverlappingLinks(oldLinks, newLinks.tail, newLink +: overlappingLinks)
        } else {
          findOverlappingLinks(oldLinks, newLinks.tail, overlappingLinks)
        }
        case None => overlappingLinks
      }
    }
    val overlappingLinks = findOverlappingLinks(preexistingLinks, linkifiedLinks)
    val nonOverlappingSpanned = if (overlappingLinks.isEmpty) {
      linkifiedSpanned
    } else {
      val spannable = linkifiedSpanned.toSpannable
      overlappingLinks.foreach(spanInfo => spannable.removeSpan(spanInfo.span))
      spannable
    }
    if (preexistingLinks.isEmpty) {
      linkifiedText
    } else {
      val spannable = nonOverlappingSpanned.toSpannable
      preexistingLinks.foreach(_.addTo(spannable))
      setSpanned(linkifiedText, spannable)
    }

  }

  protected def linkifyText(text: T, mask: Int): T

  protected def linkifyText(text: T, pattern: Pattern, scheme: String): T

  protected def linkifyText(text: T, pattern: Pattern, scheme: String, matchFilter: Linkify.MatchFilter,
                            transformFilter: Linkify.TransformFilter): T

  protected def getSpanned(text: T): Spanned

  protected def setSpanned(text: T, spanned: Spanned): T
}

