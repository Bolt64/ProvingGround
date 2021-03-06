package provingground

import provingground._
import Collections._ ; import FiniteDistribution._; import provingground._
import org.scalatest.FlatSpec
import FiniteDistribution._
import DiffbleFunction._
import LinearStructure._

class DiffbleFunctionSpec extends FlatSpec{
    val double = DiffbleFunction((x: Double) => 2 * x)((x: Double) => (y: Double) => 2 *y)

    val square = DiffbleFunction((x: Double) => x * x)((x: Double) => (y: Double) => x * y)

  "A Differentiable function" should "evaluate by apply" in {
    val fn = DiffbleFunction((x: Double) => 2 * x)((x: Double) => (y: Double) => 2 *y)

    assert(double.func(2) == 4)

    assert(square.func(3) == 9)
  }

  it should "apply gradient" in {
    val fn = DiffbleFunction((x: Double) => x * x)((x: Double) => (y: Double) => x * y)

    assert(square.grad(2)(3) == 6)

    assert(double.grad(3)(2) == 4)
  }

  it should "have expected derivatives and values for inclusion" in {
    val fn1 = Incl1[Double, Double]

    val fn2 = Incl2[Double, Double]

    assert(fn1.func(1.5) == (1.5, 0))
    assert(fn1.grad(1.5)(2.5, 3.5) == 2.5)

    assert(fn2.func(1.5) == (0, 1.5))
    assert(fn2.grad(1.5)(2.5, 3.5) == 3.5)
  }

  it should "have expected derivatives and values for projection" in {
    val fn1 = Proj1[Double, Double]

    val fn2 = Proj2[Double, Double]

    assert(fn1.func((1.5, 2.5)) == 1.5)
    assert(fn1.grad(1.5, 2.5)(3) == (3, 0))

    assert(fn2.func((1.5, 2.5)) == 2.5)
    assert(fn2.grad(1.5, 2.5)(3) == (0, 3))
  }
}
