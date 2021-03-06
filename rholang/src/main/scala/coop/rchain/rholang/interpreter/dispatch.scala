package coop.rchain.rholang.interpreter

import cats.Parallel
import cats.effect.Sync
import cats.mtl.FunctorTell
import coop.rchain.crypto.hash.Blake2b512Random
import coop.rchain.models.Channel.ChannelInstance.Quote
import coop.rchain.models.TaggedContinuation.TaggedCont.{Empty, ParBody, ScalaBodyRef}
import coop.rchain.models._
import cats.implicits._
import coop.rchain.rholang.interpreter.Runtime.RhoISpace
import coop.rchain.rholang.interpreter.accounting.{CostAccount, CostAccountingAlg}
import coop.rchain.rholang.interpreter.storage.TuplespaceAlg
import coop.rchain.rspace.pure.PureRSpace

trait Dispatch[M[_], A, K] {
  def dispatch(continuation: K, dataList: Seq[A]): M[Unit]
}

object Dispatch {

  // TODO: Make this function total
  def buildEnv(dataList: Seq[ListChannelWithRandom]): Env[Par] =
    Env.makeEnv(
      dataList
        .flatMap(_.channels)
        .map({
          case Channel(Quote(p)) => p
          case Channel(_)        => Par() // Should never happen
        }): _*
    )
}

class RholangAndScalaDispatcher[M[_]] private (
    reducer: => ChargingReducer[M],
    _dispatchTable: => Map[Long, Function1[Seq[ListChannelWithRandom], M[Unit]]]
)(implicit s: Sync[M])
    extends Dispatch[M, ListChannelWithRandom, TaggedContinuation] {

  def dispatch(continuation: TaggedContinuation, dataList: Seq[ListChannelWithRandom]): M[Unit] =
    for {
      res <- continuation.taggedCont match {
              case ParBody(parWithRand) =>
                val env     = Dispatch.buildEnv(dataList)
                val randoms = parWithRand.randomState +: dataList.toVector.map(_.randomState)
                reducer.eval(parWithRand.body)(env, Blake2b512Random.merge(randoms))
              case ScalaBodyRef(ref) =>
                _dispatchTable.get(ref) match {
                  case Some(f) => f(dataList)
                  case None    => s.raiseError(new Exception(s"dispatch: no function for $ref"))
                }
              case Empty =>
                s.unit
            }
    } yield res

}

object RholangAndScalaDispatcher {

  def create[M[_], F[_]](
      tuplespace: RhoISpace,
      dispatchTable: => Map[Long, Function1[Seq[ListChannelWithRandom], M[Unit]]],
      urnMap: Map[String, Par]
  )(
      implicit
      parallel: Parallel[M, F],
      s: Sync[M],
      ft: FunctorTell[M, Throwable]
  ): (Dispatch[M, ListChannelWithRandom, TaggedContinuation], ChargingReducer[M], Registry[M]) = {
    val pureSpace          = PureRSpace[M].of(tuplespace)
    lazy val tuplespaceAlg = TuplespaceAlg.rspaceTuplespace(pureSpace, dispatcher)
    lazy val dispatcher: Dispatch[M, ListChannelWithRandom, TaggedContinuation] =
      new RholangAndScalaDispatcher(chargingReducer, dispatchTable)
    implicit lazy val reducer: Reduce[M] =
      new Reduce.DebruijnInterpreter[M, F](tuplespaceAlg, urnMap)
    implicit lazy val costAlg      = CostAccountingAlg.unsafe[M](CostAccount(0))
    lazy val chargingReducer       = new ChargingReducer[M]
    lazy val registry: Registry[M] = new RegistryImpl(pureSpace, dispatcher)
    (dispatcher, chargingReducer, registry)
  }
}
