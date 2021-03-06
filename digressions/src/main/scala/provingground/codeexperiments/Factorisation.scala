package provingground.codeexperiments

import annotation.tailrec
//import scala.swing._
import java.awt.Dimension
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import provingground.TextToInt._
import akka.util.Timeout.durationToTimeout
import akka.pattern.ask
import scala.language.postfixOps

/** This is to experiment with using actors for background computations
		We use a Naive algorithm as this suits our purposes
 */
object Factorisation /*extends SimpleSwingApplication*/{/*
    val s = new Dimension(1500, 1500)

	case class FactoriseTask(n: Int)

	class FactorActor extends Actor{
		val factorsMemo = Stream.from(0) map (factorise(_))
		def receive = {
			case FactoriseTask(n:Int) =>
					sender ! factorsMemo(n)
					println(factorsMemo(n))
			}
	}



	@tailrec def findPrimeFactor(n: Int, m:Int = 2): Int = {
		if (m * m > n) 1
		else if (n % m == 0) m
		else findPrimeFactor(n, m+1)
		}


	@tailrec def factorise(n: Int, knownFactors: List[Int] =List()): List[Int] = {
		val factor = findPrimeFactor(n)
		if (factor == 1) n :: knownFactors else factorise(n/factor, factor :: knownFactors)
		}

	val factors = Stream.from(0) map (factorise(_))

	val system=ActorSystem("MySystem")

	import system.dispatcher

	val factorActor = system.actorOf(Props[FactorActor], "FactorActor")

	def askFactors(n: Int): Future[List[Int]] = {

		factorActor.ask(FactoriseTask(n))(2 seconds).mapTo[List[Int]]
		}

	val toFactor = new TextArea(10, 10){
			charWrap = true
			}
	val factorButton = new Button{
		text = "Factorise"
		verticalAlignment = Alignment.Top
		}
	val factorFrame = new FlowPanel{
	    contents += factorButton
	    contents += toFactor
	    border = Swing.EmptyBorder(20, 20, 20, 20)
	}

	val factorResult = new TextArea(10, 40){
			charWrap = true
			}

	val leftPanel = new BoxPanel(Orientation.Vertical){
			contents += new Label("Enter number to factorise")
			contents += factorFrame
			contents += new Label("Factors")
			contents += factorResult
			border = Swing.EmptyBorder(20, 20, 20, 20)
			}

	def top = new MainFrame{
		title = "Factorising a Number"
		contents = new BoxPanel(Orientation.Horizontal){
		    contents += leftPanel
				minimumSize = s
		}
		}
	listenTo(factorButton)

	reactions +={
		case swing.event.ButtonClicked(`factorButton`) =>
			toFactor.text match {
				case Int(m) if m>=0 =>
						val ans = askFactors(m.toInt)
						Await.ready(ans, 5 seconds)
						ans onComplete {
							case Success(s: List[Int]) => factorResult.text = s.toString; println(s)
							case Failure(_) => factorResult.text = " could not compute the result"; println("failed")
							}
				case _ => factorResult.text = "I can only factorize non-negative integers"
				}
		}*/
	}
