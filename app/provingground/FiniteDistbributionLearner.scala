package provingground

import LearningSystem._
import Collections._
import annotation._


/**
 * A combinator for learning systems with state finite distributions on vertices.
 * Systems are built from components labeled by elements of a set M.
 * The state also has weights for these components.
 * The components are built from: moves (partial functions), partial combinations and islands.
 *  
 */
object FiniteDistbributionLearner {
	/** 
	 *  Finite Distributions
	 */
	type FD[V] = FiniteDistribution[V] 
	
	/**
	 * Differentiable functions - functions with given gradient.
	 */
	type DF[X, Y] = DiffbleFunction[X, Y]
	
	
	// Generic helpers
	
	/**
	 * identity smooth function.
	 */
	def Id[X] = DiffbleFunction((x: X) => x)((x : X) => {(y: X) => y})
	
		
	/**
	 * differentiable function x -> (x, x)
	 */
	def pair[V] = {
	  def func(d: FD[V]) = {
	    val pmf= (for (x <- d.support; y <- d.support) yield Weighted((x, y), d(x) * d(y))).toSeq
	    FiniteDistribution(pmf)
	  }
	  
	  def grad(d: FD[V])(pd: FD[(V, V)]) = {
	    val rawpmf = pd.pmf.flatMap(ab => Seq(Weighted(ab.elem._1, d(ab.elem._2)), Weighted(ab.elem._2, d(ab.elem._1))))
	    FiniteDistribution(rawpmf).flatten
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	/**
	 * declare the gradient to be the identity - to be used for approximations, pruning, sampling.
	 */
	def formalSmooth[A](f: A => A) = DiffbleFunction(f)((a : A) => {(b : A) => b})
	
	
	
	// Helpers for operations on pairs of distributions.
	

	private def dstsum[M, V](fst: (FD[M], FD[V]), scnd: (FD[M], FD[V])) = (fst._1 ++ scnd._1, fst._2++ scnd._2)
	
	private def dstmult[M, V](fst: (FD[M], FD[V]), scnd: Double) = (fst._1 * scnd, fst._2 * scnd)
	
	private def dstdot[M, V](fst: (FD[M], FD[V]), scnd: (FD[M], FD[V])) = (fst._1 dot scnd._1) + (fst._2 dot scnd._2)
	

	
	// Building dynamics on FD[V]
	
	/**
	 * smooth function applying move wherever applicable 
	 */
	def moveFn[V](f: V => Option[V]) = {
	  def func(d: FD[V]) = {
	    val rawpmf = for (x<- d.support.toSeq; y<- f(x)) yield Weighted(y, d(x))
	    FiniteDistribution(rawpmf).flatten
	  }
	  
	  def grad(d: FD[V])(w: FD[V]) = {
	    val rawpmf = for (x<- d.support.toSeq; y<- f(x)) yield Weighted(x, w(y))
	    FiniteDistribution(rawpmf).flatten
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	def combinationFn[V](f: (V, V) => Option[V]) = {
	  def func(d: FD[V]) = {
	    val rawpmf = for (a <- d.support.toSeq; b <- d.support.toSeq; y <- f(a, b)) yield
	    		Weighted(y, d(a) * d(b))
	    FiniteDistribution(rawpmf).flatten
	  }
	  
	  def grad(d: FD[V])(w: FD[V]) = {
	    val rawpmf = (for (a <- d.support.toSeq; b <- d.support.toSeq; y <- f(a, b)) yield
	    		Seq(Weighted(a, w(y) * d(b)), Weighted(b, w(y) * d(b)))).flatten
	    FiniteDistribution(rawpmf).flatten
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	// Building dynamics (FD[M], FD[V]) => FD[V] 
	
	private type DynFn[M, V] = DF[(FD[M], FD[V]), FD[V]]
	    
	
	/**
	 * smooth function corresponding to adding the V distributions for two given smooth functions.
	 *  
	 */
	def sumFn[M, V](fst: => DynFn[M, V], scnd : => DynFn[M, V]) : DynFn[M, V] = {
	  def func(st : (FD[M], FD[V])) = fst(st) ++ scnd(st)
	  
	  def grad(st: (FD[M], FD[V]))(w: FD[V]) ={
	    dstsum(fst.grad(st)(w), scnd.grad(st)(w))
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	/**
	 * differentiable function from a given one by composing with scalar multiplication.
	 * Namely, (s, x) -> s * f(x), with gradient having weights on both s and x. 
	 */
	def scProdFn[V](fn : DF[FD[V], FD[V]]) = {
	  def func(cv: (Double, FD[V])) = fn(cv._2) * cv._1
	  
	  def grad(cv: (Double, FD[V]))(w: FD[V]) = {
	    val x = fn.grad(cv._2)(w)
	    (x dot cv._2, x * cv._1)
	  } 
	  
	  DiffbleFunction(func)(grad)
	}

	
	
	/**
	 * Creates an atom at a parameter with given weight.
	 */
	private def atM[M, V](cv: (Double, FD[V]), m: M) = {
	  val dstM = FiniteDistribution.empty[M] + (m, cv._1)  
	  (dstM, cv._2)
	}
	
		/**
	 * binds a scalar (multiplication) constant to a particular parameter.
	 */
	def bindFn[M, V](m: M, fn: DF[(Double, FD[V]), FD[V]]) ={
	  def func(csv: (FD[M], FD[V])) = {
	    val c = csv._1(m)
	    val v = csv._2
	    fn((c, v))
	  }
	  
	  def grad(csv: (FD[M], FD[V]))(w: FD[V]) = {
	    val c = csv._1(m)
	    val v = csv._2
	    val cterm = fn.grad((c, v))(w)
	    atM(cterm, m)
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	/**
	 * The weighted term typically corresponding to a move or a combination
	 */
	def wtdDyn[M, V](m: M,fn : DF[FD[V], FD[V]]) : DynFn[M, V] = bindFn(m, scProdFn(fn))
	
	/**
	 * the zero differentiable function
	 */	
	private def zeroMVV[M, V] : DynFn[M, V] = {
	  def func(a: (FD[M], FD[V])) = FiniteDistribution.empty[V]
	  
	  def grad(a: (FD[M], FD[V]))(b: FD[V]) = (FiniteDistribution.empty[M], FiniteDistribution.empty[V])
	  
	  DiffbleFunction(func)(grad)
	}
	
	/**
	 * Linear combination of dynamical systems
	 */
	def linComb[M, V](dyns: Map[M, DF[FD[V], FD[V]]]) = {
	  val syslst = for ((m, f) <- dyns) yield wtdDyn(m, f)
	  (zeroMVV[M, V] /: syslst)((a, b) => sumFn(a, b))
	}
	
	
	
	
	
	// Building dynamics on (FD[M], FD[V])
	
	private type DS[M, V] = DF[(FD[M], FD[V]), (FD[M], FD[V])]
	
	def sumDF[M, V](fst: => DS[M, V], scnd : => DS[M, V]) = {
	  def func(st : (FD[M], FD[V])) = dstsum(fst(st), scnd(st))
	  
	  def grad(st: (FD[M], FD[V]))(w: (FD[M], FD[V])) ={
	    dstsum(fst.grad(st)(w), scnd.grad(st)(w))
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	def foldDF[M, V](base: DS[M, V], comps: Map[V, DS[M, V]]) = {
	  def func(arg: (FD[M], FD[V])) = {
	    val l = for ((v, f) <- comps) yield dstmult(f(arg), arg._2(v)) 
	    (base(arg) /: l)(dstsum)
	  }
	  
	  def grad(arg: (FD[M], FD[V]))(vect: (FD[M], FD[V])) = {
	    val l = for ((v, f) <- comps) yield {
	      val prob = dstdot(f.grad(arg)(vect), arg)
	      val term = dstmult(f.grad(arg)(vect), arg._2(v))
	      (term._1, term._2 + (v, prob))
	    }
	    (base.grad(arg)(vect) /: l)(dstsum)
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	
	/**
	 * Extend differentiable function by identity on M.
	 */
	def extendM[M, V](fn: DF[(FD[M], FD[V]), FD[V]]) = {
	  def func(mv: (FD[M], FD[V])) = (mv._1, fn(mv))
	  
	  def grad(mv: (FD[M], FD[V]))(mw: (FD[M], FD[V])) = (mw._1 ++ fn.grad(mv)(mw._2)._1, fn.grad(mv)(mw._2)._2)
	  
	  DiffbleFunction(func)(grad)
	}
	
	def projectV[M, V] = {
	  def func(mv: (FD[M], FD[V])) = mv._2
	  
	  def grad(mv: (FD[M], FD[V]))(v : FD[V]) = (FiniteDistribution.empty[M], v)
	  
	  DiffbleFunction(func)(grad)
	}
	
	/**
	 * prune the distribution on vertices without changing the one on M.
	 */
	def pruneV[M, V](t: Double) = formalSmooth((mv: (FD[M], FD[V])) => (mv._1, mv._2 normalized(t)))
	
	/**
	 * prune a distribution
	 */
	def prune[V](t: Double) = formalSmooth((d: FD[V]) => d normalized(t))	

	
	/**
	 * function and gradient after scalar multiplication by the parameter of the isle,
	 * (p_M, p_V) -> (p_M(m) * f(p_M, p_V))
	 */
	def isleMultFn[M, V](fn: DynFn[M, V], m: M) = {
	  def func(csv: (FD[M], FD[V])) = {
	    val c = csv._1(m)
	    val v = csv._2
	    fn((csv._1, v)) * c
	  }
	  
	  def grad(csv: (FD[M], FD[V]))(w: FD[V]) = {
	    val c = csv._1(m)
	    val v = csv._2
	    val mv = fn.grad((csv._1, v))(w)
	    val shift = mv._2 dot v
	    (mv._1 + (m, shift), mv._2)
	  }
	  
	  DiffbleFunction(func)(grad)
	}

	

	
	/**
	 * smooth function creating initial distribution for isle creating an object v with weight given by parameter t.
	 * To actually create an isle, we compose this with the corresponding mixin.
	 */
	def simpleIsleInitFn[M, V](v: V, t: M) = {
	  def func(ds: (FD[M], FD[V])) ={
	    val p = ds._1(t)
	    (ds._1, ds._2 * (1.0 - p) + (v, p))
	  }
	  
	  def grad(ds: (FD[M], FD[V]))(ws: (FD[M], FD[V])) ={
	    val p = ds._1(t)
	    val c = ws._1(t)
	    val shift = (ds._2 filter ((x: V) => x != v)) * (1.0 - p)
	    (ws._1 + (t, c), shift)
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	/**
	 * Spawns an island, that can be mixed in with other dynamics
	 * One should call this with f modified by updating depth, which should affect number of steps.
	 *
	 */
	def spawnSimpleIsle[M, V](v: V, t: M, m : M, f : => DynFn[M, V]) = {
	  val g = simpleIsleInitFn(v, t) andThen f
	  isleMultFn(g, m)
	}
	

	
	/**
	 * step for learning based on feedback.
	 */
	def learnstep[M, V](f: DF[(FD[M], FD[V]), (FD[M], FD[V])], epsilon: Double, feedback: (FD[M], FD[V]) => (FD[M], FD[V])) = {
	  (init : (FD[M], FD[V])) => 
	    val fb = feedback(f(init)._1, f(init)._2)
	    (init._1 ++ (fb._1 * epsilon), init._2 ++ (fb._2 * epsilon))
	}
	
	
		  
	/**
	 * Iterate a differentiable function.
	 */
	@tailrec def iterateDiffble[X](fn: DF[X, X], n: Int, accum: DF[X, X] = Id[X]): DF[X, X] = {
		if (n<1) accum else iterateDiffble(fn, n-1, accum andThen fn)
	}
	
	/** 
	 *  Iterate a diffble function given depth
	 */
	@tailrec def iterateDiffbleDepth[X](fn: => DF[X, X], steps: Int, depth: Int, accum: => DF[X, X] = Id[X]): DF[X, X] = {
		if (steps<math.pow(2, depth)) accum else iterateDiffbleDepth(fn, steps - math.pow(2, depth).toInt, depth, accum andThen fn)
	}
	
	
	class IterDynSys[M, V](dyn : => DynFn[M, V], steps: Int, depth: Int){
	  def iter(d: Int) = iterateDiffbleDepth(next, steps, d)
	  
	  def iterdeeper(d: Int) = if (steps < math.pow(2, depth + 1)) Id[(FD[M], FD[V])] else iterateDiffbleDepth(next, steps, depth+1)
	  
	  def iterV(d: Int) = iter(d) andThen projectV[M, V]
	  
	  /**
	   * add a simple island one step deeper and create a system adding depth.
	   * 
	   * @param v new variable created in import.
	   * @param t coefficient of the new variable created.
	   * @param m weight for island formation.
	   * @param export exporting from the island.
	   */
	  def withIsle(v: V, t: M, m : M, export : => DF[FD[V], FD[V]], d: Int = depth, isleDepth: Int => Int) : DynFn[M, V] = {
	    def iternext = iterateDiffbleDepth(extendM(withIsle(v, t, m, export, d+1, isleDepth)), steps, isleDepth(d))
	    def isle = spawnSimpleIsle[M, V](v: V, t: M, m : M, iternext andThen projectV[M, V]) andThen export
	    sumFn(dyn, isle)
	  }
	  
	  /**
	   * make a simple island one step deeper.
	   * 
	   * @param v new variable created in import.
	   * @param t coefficient of the new variable created.
	   * @param m weight for island formation.
	   * @param export exporting from the island.
	   */
	  def mkIsle(v: V, t: M, m : M, export : => DF[FD[V], FD[V]], d: Int = depth, isleDepth: Int => Int) : DynFn[M, V] = {
	    def iternext = iterateDiffbleDepth(iter(isleDepth(d)), steps, isleDepth(d))
	    spawnSimpleIsle[M, V](v: V, t: M, m : M, iternext andThen projectV[M, V]) andThen export
	  }
	  
	  def addIsle(v: V, t: M, m : M, export : => DF[FD[V], FD[V]], isleDepth: Int => Int = (n) => n + 1) = new IterDynSys(
	      withIsle(v: V, t: M, m : M, export, depth, isleDepth), steps, depth)
	  
	  def next = extendM(dyn)
	}
	
	type DynF[M, V] = DF[(FD[M], FD[V]), (FD[M], FD[V])]
	
	
	def sumF[M, V](fst: => DynF[M, V], scnd : => DynF[M, V]) = {
	  def func(st : (FD[M], FD[V])) = dstsum(fst(st), scnd(st))
	  
	  def grad(st: (FD[M], FD[V]))(w: (FD[M], FD[V])) ={
	    dstsum(fst.grad(st)(w), scnd.grad(st)(w))
	  }
	  
	  DiffbleFunction(func)(grad)
	}
	
	/*
	 * Isle independent of state, does not include lambda's
	 * We are given a dynamical system base at each depth n, 
	 * and an isle function depending on a given dynamical system and dpeth. 
	 * We get another one f at each depth n by the recurence
	 * f(n) = base(n) + isle(n+1)(f(n+1))
	 * 
	 * 
	 */ 
	def mixinIsle[M, V](base : => Int => DynFn[M, V], isle: Int => DynFn[M, V] => DynFn[M, V]): Int => DynFn[M, V] = {
	  def rec(g : => Int => DynFn[M, V])(n: Int) = sumFn(base(n), isle(n+1)(g(n+1)))
	  def f: Int => DynFn[M, V] = rec(f)
	  f
	}
	
	
	/*
	 * Mix in isles corresponding to elements of V, for instance lambdas
	 * Should really have optional isles.
	  */
	def mixinAllIsles[M, V](base : => Int => DynF[M, V], isles: Map[V, Int => DynF[M, V] => DynF[M, V]]): Int => DynF[M, V] = {
	  def rec(g : => Int => DynF[M, V])(n: Int) = {
	    val isllst = for ((v, f) <- isles) yield (v, isles(v)(n)(g(n+1)))
	    foldDF[M, V](sumF(base(n), g(n)), isllst)
	  }
	  def f: Int => DynF[M, V] = rec(f)
	  f
	}
}