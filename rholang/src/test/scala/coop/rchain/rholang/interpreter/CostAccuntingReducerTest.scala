package coop.rchain.rholang.interpreter
import org.scalatest.{FlatSpec, Matchers}

class CostAccuntingReducerTest extends FlatSpec with Matchers {

  behavior of "Cost accounting in Reducer"

  it should "charge for the successful substitution" in {
    pending
  }

  it should "charge for failed substitution" in {
    pending
  }

  it should "stop if OutOfPhloError is returned from RSpace" in {
    pending
  }

  it should "update the cost account after going back from RSpace" in {
    pending
  }

  it should "stop interpreter threads as soon as deploy runs out of phlo" in {
    pending
  }
}
