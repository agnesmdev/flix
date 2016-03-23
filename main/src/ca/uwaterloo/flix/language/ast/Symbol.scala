package ca.uwaterloo.flix.language.ast

import scala.collection.mutable

object Symbol {

  /**
    * Returns the table symbol for the given fully qualified name.
    */
  def mkTableSym(fqn: String): TableSym = {
    if (!fqn.contains('/'))
      return new TableSym(List.empty, fqn, SourceLocation.Unknown)

    val index = fqn.indexOf('/')
    val namespace = fqn.substring(0, index).split('.').toList
    val name = fqn.substring(index + 1, fqn.length)
    new TableSym(namespace, name, SourceLocation.Unknown)
  }


  /**
    * Variable Symbol.
    *
    * @param id   the globally unique name of the symbol.
    * @param text the original name, as it appears in the source code, of the symbol
    * @param tpe  the type of the symbol.
    * @param loc  the source location associated with the symbol.
    */
  final class VarSym(val id: Int, val text: String, val tpe: Type, val loc: SourceLocation) {
    /**
      * Returns `true` if this symbol is equal to `that` symbol.
      */
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: VarSym => this.id == that.id
      case _ => false
    }

    /**
      * Returns the hash code of this symbol.
      */
    override val hashCode: Int = id.hashCode()

    /**
      * Human readable representation.
      */
    override val toString: String = text + "$" + id
  }

  /**
    * Table Symbol.
    *
    * @param loc the source location associated with the symbol.
    */
  final class TableSym(val namespace: List[String], val name: String, val loc: SourceLocation) {
    /**
      * Returns `true` if this symbol is equal to `that` symbol.
      */
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: TableSym => this.namespace == that.namespace && this.name == that.name
      case _ => false
    }

    /**
      * Returns the hash code of this symbol.
      */
    override val hashCode: Int = 7 * namespace.hashCode() + 11 * name.hashCode

    /**
      * Human readable representation.
      */
    override val toString: String = namespace.mkString(".") + "/" + name
  }


  /**
    * Companion object for the [[Resolved]] class.
    */
  // TODO: remove and replace by better symbols
  @deprecated("to be removed", "0.1.0")
  object Resolved {

    private val cache = mutable.HashMap.empty[List[String], Resolved]

    def mk(name: String): Resolved = {
      if (name.contains("/")) {
        val index = name.indexOf("/")
        val (ns, ident) = name.splitAt(index)
        mk(ns.split("\\.").toList ::: ident.substring(1) :: Nil)
      } else
        mk(List(name))
    }

    def mk(parts: List[String]): Resolved = {
      cache.getOrElseUpdate(parts, new Resolved(parts))
    }
  }

  /**
    * Represents a resolved name.
    *
    * @param parts the parts of the name.
    */
  @deprecated("to be removed", "0.1.0")
  final class Resolved private(val parts: List[String]) {

    /**
      * Returns the fully qualified name of `this` as a string.
      */
    def fqn: String = parts match {
      case x :: Nil => x
      case xs => xs.init.mkString(".") + "/" + xs.last
    }

    /**
      * Returns `true` if this resolved name is equal to `obj` resolved name.
      */
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: Resolved => this eq that
      case _ => false
    }

    /**
      * Returns the hash code of this resolved name.
      */
    override val hashCode: Int = parts.hashCode()

    /**
      * Human readable representation.
      */
    override val toString: String = fqn
  }

}