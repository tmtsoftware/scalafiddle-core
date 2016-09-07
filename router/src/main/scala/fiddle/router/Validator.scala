package fiddle.router

import scala.util.{Failure, Success, Try}

trait Validator {
  def isInvalid(input: String): Option[String]
}

case object EmptyValidator extends Validator {
  override def isInvalid(input: String): Option[String] = None
}

case class IntValidator(min: Int = 0, max: Int = Int.MaxValue) extends Validator {
  override def isInvalid(input: String): Option[String] = {
    Try {
      val i = input.toInt
      min <= i && i < max
    } match {
      case Failure(_) | Success(false) => Some(s"Expected an integer between $min and $max")
      case _ => None
    }
  }
}

case class ListValidator(options: String*) extends Validator {
  override def isInvalid(input: String): Option[String] = {
    if(options.contains(input))
      None
    else
      Some(s"$input is not a valid value")
  }
}
