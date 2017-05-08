package fiddle.shared

sealed trait CompilerState

object CompilerState {

  case object Initializing extends CompilerState

  case object Ready extends CompilerState

  case object Compiling extends CompilerState

}
