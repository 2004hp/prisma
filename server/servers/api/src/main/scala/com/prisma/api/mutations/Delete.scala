package com.prisma.api.mutations

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.api.ApiDependencies
import com.prisma.api.connector._
import com.prisma.api.mutactions.{DatabaseMutactions, ServerSideSubscriptions, SubscriptionEvents}
import com.prisma.api.schema.{APIErrors, ObjectTypeBuilder}
import com.prisma.shared.models.IdType.Id
import com.prisma.shared.models.{Model, Project}
import com.prisma.util.coolArgs.CoolArgs
import sangria.schema

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

case class Delete(
    model: Model,
    modelObjectTypes: ObjectTypeBuilder,
    project: Project,
    args: schema.Args,
    dataResolver: DataResolver
)(implicit apiDependencies: ApiDependencies)
    extends SingleItemClientMutation {

  implicit val system: ActorSystem             = apiDependencies.system
  implicit val materializer: ActorMaterializer = apiDependencies.materializer

  var deletedItemOpt: Option[PrismaNode] = None
  val requestId: Id                      = "" // dataResolver.requestContext.map(_.requestId).getOrElse("")

  val coolArgs            = CoolArgs(args.raw)
  val where: NodeSelector = coolArgs.extractNodeSelectorFromWhereField(model)

  override def prepareMutactions(): Future[TopLevelDatabaseMutaction] = {
    dataResolver
      .getNodeByWhere(where)
      .andThen {
        case Success(x) => deletedItemOpt = x.map(dataItem => dataItem)
      }
      .map { _ =>
        val itemToDelete = deletedItemOpt.getOrElse(throw APIErrors.NodeNotFoundForWhereError(where))
        val mutaction    = DatabaseMutactions(project).getMutactionsForDelete(where, itemToDelete)
//        val subscriptionMutactions = SubscriptionEvents.extractFromSqlMutactions(project, mutationId, mutaction)
//        val sssActions             = ServerSideSubscriptions.extractFromMutactions(project, mutaction, requestId)

        mutaction
      }
  }

  override def getReturnValue(results: MutactionResults): Future[ReturnValueResult] = Future.successful(ReturnValue(deletedItemOpt.get))
}
