/*
 * Copyright 2014 Dennis Vis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talares.api.actors

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import org.specs2.time.NoTimeConversions
import org.talares.api.Talares
import org.talares.api.actors.messages.{ExecutorMessages, FetcherMessages}
import org.talares.api.actors.mock.MockFetcher
import org.talares.api.datatypes.JsonReadable
import org.talares.api.datatypes.items._
import org.talares.api.datatypes.items.stubs.ItemStubs
import org.talares.api.queries._

import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
 * @author Dennis Vis
 * @since 0.1.0
 */
class FetcherSpec extends TestKit(ActorSystem("fetcher-spec", ConfigFactory.load()))
with NoTimeConversions
with Mockito
with SpecificationLike {

  implicit val app = mock[Talares]

  val webserviceLocationStub = "http://www.example.com/cd_webservice/odata.svc"
  val mockRequest = mock[FetcherMessages.Task[Page]]

  val idQueryStub = Query / "Pages" %("PublicationId" -> 1, "ItemId" -> 2)
  val searchQueryStub = Query / "Pages" $ ("Url" ==| "Foo")

  val idUrlStub = webserviceLocationStub + idQueryStub.value
  val searchUrlStub = webserviceLocationStub + searchQueryStub.value

  val executorIDTaskStub = ExecutorMessages.Execute(mockRequest, idUrlStub)
  val executorSearchTaskStub = ExecutorMessages.Execute(mockRequest, searchUrlStub)

  def mockFetcherRef[T](implicit jsonReadable: JsonReadable[T], classTag: ClassTag[T]) =
    MockFetcher.mockFetcherRef[T](app, testActor)

  def mockFetcher[T](implicit jsonReadable: JsonReadable[T], classTag: ClassTag[T]) = mockFetcherRef[T].underlyingActor

  def failingFetcherRef[T](implicit jsonReadable: JsonReadable[T], classTag: ClassTag[T]) =
    MockFetcher.failingFetcherRef[T](app, testActor)

  sequential

  "A Fetcher" should {

    "create correct endpoint" in {
      mockFetcher[Binary].endpoint == "Binaries"
      mockFetcher[BinaryContent].endpoint == "BinaryContents"
      mockFetcher[BinaryVariant].endpoint == "BinaryVariants"
      mockFetcher[Component].endpoint == "Components"
      mockFetcher[ComponentPresentation].endpoint == "ComponentPresentations"
      mockFetcher[CustomMeta].endpoint == "CustomMetas"
      mockFetcher[Keyword].endpoint == "Keywords"
      mockFetcher[Page].endpoint == "Pages"
      mockFetcher[PageContent].endpoint == "PageContents"
      mockFetcher[Publication].endpoint == "Publications"
      mockFetcher[Schema].endpoint == "Schemas"
      mockFetcher[StructureGroup].endpoint == "StructureGroups"
      mockFetcher[Template].endpoint == "Templates"
    }

    "create an ID query" in {
      mockFetcher[Page].createIDQuery(Seq("PublicationId" -> 1, "ItemId" -> 2)) must be equalTo idQueryStub
    }

    "create a search query" in {
      mockFetcher[Page].createSearchQuery(Seq("Url" -> "Foo")) must be equalTo searchQueryStub
    }

    "create a URL" in {
      mockFetcher[Page].createUrl(webserviceLocationStub, idQueryStub) must be equalTo idUrlStub
    }

    "create URL task" in {
      mockFetcher[Page].createURLTask(mockRequest, idUrlStub) must be equalTo executorIDTaskStub
    }

    "create query task" in {
      mockFetcher[Page].createQueryTask(
        mockRequest, webserviceLocationStub, idQueryStub
      ) must be equalTo executorIDTaskStub
    }

    "create ID task" in {
      mockFetcher[Page].createIDTask(
        mockRequest, webserviceLocationStub, "PublicationId" -> 1, "ItemId" -> 2
      ) must be equalTo executorIDTaskStub
    }

    "create search task" in {
      mockFetcher[Page].createSearchTask(
        mockRequest, webserviceLocationStub, "Url" -> "Foo"
      ) must be equalTo executorSearchTaskStub
    }

    "fetch by URI" in {

      val task = FetcherMessages.FetchByURI[Page](
        testActor, webserviceLocationStub + "/Pages(PublicationId=1,ItemId=2)"
      )
      val expected = FetcherMessages.SingleResult(task, ItemStubs.pageStub.as[Page])

      mockFetcherRef[Page] ! task

      receiveOne(1 second) must be equalTo expected
    }

    "fetch by ID" in {

      val task = FetcherMessages.FetchByID[Page](
        testActor, webserviceLocationStub, "PublicationId" -> 1, "ItemId" -> 2
      )
      val expected = FetcherMessages.SingleResult(task, ItemStubs.pageStub.as[Page])

      mockFetcherRef[Page] ! task

      receiveOne(1 second) must be equalTo expected
    }

    "fetch by search" in {

      val task = FetcherMessages.FetchBySearch[Page](
        testActor, webserviceLocationStub, "Url" -> "/path"
      )
      val expected = FetcherMessages.MultiResult(task, ItemStubs.pagesStub.as[Seq[Page]])

      mockFetcherRef[Page] ! task

      receiveOne(1 second) must be equalTo expected
    }

    "fetch by query" in {

      val query = Query / "Pages" %("PublicationId" -> 1, "ItemId" -> 2)
      val task = FetcherMessages.FetchByQuery[Page](
        testActor, webserviceLocationStub, query
      )
      val expected = FetcherMessages.SingleResult(task, ItemStubs.pageStub.as[Page])

      mockFetcherRef[Page] ! task

      receiveOne(1 second) must be equalTo expected
    }

    "handle failure" in {

      val task = FetcherMessages.FetchByID[Page](
        testActor, webserviceLocationStub, "PublicationId" -> 1, "ItemId" -> 2
      )

      failingFetcherRef[Page] ! task

      receiveOne(1 second) must beAnInstanceOf[FetcherMessages.Failure[Page]]
    }
  }

  step(shutdown())
}