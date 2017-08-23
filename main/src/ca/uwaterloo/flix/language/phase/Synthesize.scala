/*
 * Copyright 2017 Magnus Madsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.uwaterloo.flix.language.phase

import ca.uwaterloo.flix.api.Flix
import ca.uwaterloo.flix.language.CompilationError
import ca.uwaterloo.flix.language.ast._
import ca.uwaterloo.flix.language.ast.TypedAst._
import ca.uwaterloo.flix.util.{InternalCompilerException, Validation}
import ca.uwaterloo.flix.util.Validation._

import scala.collection.mutable

/**
  * Synthesizes (generates) boiler-plate code for equality and toString definitions.
  */
object Synthesize extends Phase[Root, Root] {

  /**
    * Performs synthesis on the given ast `root`.
    */
  def run(root: Root)(implicit flix: Flix): Validation[Root, CompilationError] = {
    // Put the GenSym object into implicit scope.
    implicit val _ = flix.genSym

    // A mutable map from symbols to definitions.
    val newDefs = mutable.Map.empty[Symbol.DefnSym, Def]

    // A mutable map from types to their equality operator.
    val mutEqualityOps = mutable.Map.empty[Type, Symbol.DefnSym]

    // A mutable map from types to their toString operator.
    val mutToStringOps = mutable.Map.empty[Type, Symbol.DefnSym]

    // The source location used for all code generated by the current phase.
    val sl = SourceLocation.Unknown

    /**
      * Introduces synthesized definitions for the given definition `defn`
      * and rewrites its expression to call those definitions.
      */
    def visitDef(defn: Def): Def = {
      defn.copy(exp = visitExp(defn.exp))
    }

    /**
      * Introduces synthesized definitions for the given expression `exp0`.
      */
    def visitExp(exp0: Expression): Expression = exp0 match {
      case Expression.Unit(loc) => exp0
      case Expression.True(loc) => exp0
      case Expression.False(loc) => exp0
      case Expression.Char(lit, loc) => exp0
      case Expression.Float32(lit, loc) => exp0
      case Expression.Float64(lit, loc) => exp0
      case Expression.Int8(lit, loc) => exp0
      case Expression.Int16(lit, loc) => exp0
      case Expression.Int32(lit, loc) => exp0
      case Expression.Int64(lit, loc) => exp0
      case Expression.BigInt(lit, loc) => exp0
      case Expression.Str(lit, loc) => exp0
      case Expression.Wild(tpe, eff, loc) => exp0
      case Expression.Var(sym, tpe, eff, loc) => exp0
      case Expression.Def(sym, tpe, eff, loc) => exp0
      case Expression.Hook(hook, tpe, eff, loc) => exp0

      case Expression.Lambda(fparams, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Lambda(fparams, e, tpe, eff, loc)

      case Expression.Apply(exp, args, tpe, eff, loc) =>
        val e = visitExp(exp)
        val es = args map visitExp
        Expression.Apply(e, es, tpe, eff, loc)

      case Expression.Unary(op, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Unary(op, e, tpe, eff, loc)

      case Expression.Binary(op, exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)

        // Check whether to synthesize an equality definition.
        // Note: Synthesis takes place after monomorphization.
        //       Hence all types are fully known and usages of
        //       the equality operator have already been specialized
        //       where applicable.

        op match {
          case BinaryOperator.Equal => mkApplyEq(e1, e2)
          case BinaryOperator.NotEqual => mkApplyNeq(e1, e2)
          case _ => Expression.Binary(op, e1, e2, tpe, eff, loc)
        }

      case Expression.Let(sym, exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.Let(sym, e1, e2, tpe, eff, loc)

      case Expression.LetRec(sym, exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.LetRec(sym, e1, e2, tpe, eff, loc)

      case Expression.IfThenElse(exp1, exp2, exp3, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        val e3 = visitExp(exp3)
        Expression.IfThenElse(e1, e2, e3, tpe, eff, loc)

      case Expression.Match(exp, rules, tpe, eff, loc) =>
        val e = visitExp(exp)
        val rs = rules map {
          case MatchRule(pat, guard, body) => MatchRule(pat, guard, visitExp(body))
        }
        Expression.Match(e, rs, tpe, eff, loc)

      case Expression.Switch(rules, tpe, eff, loc) =>
        val rs = rules map {
          case (cond, body) => (visitExp(cond), visitExp(body))
        }
        Expression.Switch(rs, tpe, eff, loc)

      case Expression.Tag(sym, tag, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Tag(sym, tag, e, tpe, eff, loc)

      case Expression.Tuple(elms, tpe, eff, loc) =>
        val es = elms map visitExp
        Expression.Tuple(es, tpe, eff, loc)

      case Expression.Ref(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Ref(e, tpe, eff, loc)

      case Expression.Deref(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Deref(e, tpe, eff, loc)

      case Expression.Assign(exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.Assign(e1, e2, tpe, eff, loc)

      case Expression.Existential(fparam, exp, eff, loc) =>
        val e = visitExp(exp)
        Expression.Existential(fparam, e, eff, loc)

      case Expression.Universal(fparam, exp, eff, loc) =>
        val e = visitExp(exp)
        Expression.Universal(fparam, e, eff, loc)

      case Expression.Ascribe(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Ascribe(e, tpe, eff, loc)

      case Expression.Cast(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Cast(e, tpe, eff, loc)

      case Expression.NativeConstructor(constructor, args, tpe, eff, loc) =>
        val as = args map visitExp
        Expression.NativeConstructor(constructor, as, tpe, eff, loc)

      case Expression.NativeField(field, tpe, eff, loc) =>
        Expression.NativeField(field, tpe, eff, loc)

      case Expression.NativeMethod(method, args, tpe, eff, loc) =>
        val as = args map visitExp
        Expression.NativeMethod(method, as, tpe, eff, loc)

      case Expression.UserError(tpe, eff, loc) =>
        Expression.UserError(tpe, eff, loc)

    }

    /**
      * Returns an expression that compares `e1` and `e2` for equality possibly by
      * introducing a fresh equality definition based on their types.
      */
    def mkApplyEq(exp1: Expression, exp2: Expression): Expression = {
      // Compute the type of the two expressions (which must be the same).
      val tpe = if (exp1.tpe == exp2.tpe) exp1.tpe else throw InternalCompilerException(s"Non-equal types.")

      // Find (or create) the symbol of the equality definition.
      val sym = getOrMkEq(tpe)

      // Construct a call to the symbol passing the arguments `e1` and `e2`.
      val e = Expression.Def(sym, Type.mkArrow(List(tpe, tpe), Type.Bool), Eff.Pure, sl)
      val args = exp1 :: exp2 :: Nil
      Expression.Apply(e, args, Type.Bool, Eff.Pure, sl)
    }

    /**
      * Returns an expression that compares `e1` and `e2` for in-equality possibly by
      * introducing a fresh equality definition based on their types.
      *
      * See also mkApplyEq.
      */
    def mkApplyNeq(exp1: Expression, exp2: Expression): Expression = {
      // Compute the equality of `exp1` and `exp2.
      val e = mkApplyEq(exp1, exp2)

      // Negate the result.
      Expression.Unary(UnaryOperator.LogicalNot, e, Type.Bool, Eff.Pure, SourceLocation.Unknown)
    }

    /**
      * Returns the definition symbol of the equality operator for the given type `tpe`.
      *
      * If no such definition exists, it is created.
      */
    def getOrMkEq(tpe: Type): Symbol.DefnSym = mutEqualityOps.getOrElse(tpe, {

      // TODO: XXX: We should lookup the existence of any "eq" operator here. And not during monomorphization.

      // Introduce a fresh symbol for the equality operator.
      val sym = Symbol.freshDefnSym("eq")

      // Immediately update the equality map.
      // We must do this to support equality operators on recursive
      // data structures where equality is defined inductively.
      mutEqualityOps += (tpe -> sym)

      // Construct two fresh variable symbols for the formal parameters.
      val freshX = Symbol.freshVarSym("x")
      val freshY = Symbol.freshVarSym("y")

      // Construct the two formal parameters.
      val paramX = FormalParam(freshX, Ast.Modifiers.Empty, tpe, sl)
      val paramY = FormalParam(freshY, Ast.Modifiers.Empty, tpe, sl)

      // Annotations and modifiers.
      val ann = Ast.Annotations.Empty
      val mod = Ast.Modifiers.Empty

      // Type and formal parameters.
      val tparams = Nil
      val fparams = paramX :: paramY :: Nil

      // The body expression.
      val exp = mkEqExp(tpe, freshX, freshY)

      // The definition type.
      val lambdaType = Type.mkArrow(List(tpe, tpe), Type.Bool)

      // Assemble the definition.
      val defn = Def(None, ann, mod, sym, tparams, fparams, exp, lambdaType, Eff.Pure, sl)

      // Add it to the map of new definitions.
      newDefs += (defn.sym -> defn)

      // And return its symbol.
      defn.sym
    })

    // TODO: DOC
    // TODO: Rename Arguments
    def mkEqExp(eqType: Type, varX: Symbol.VarSym, varY: Symbol.VarSym): Expression = {

      /*
       * The source location used for the generated expressions.
       */
      val loc = SourceLocation.Unknown

      /*
       * An ordinary binary equality test to be used for primitive types.
       */
      val exp1 = Expression.Var(varX, eqType, Eff.Pure, loc)
      val exp2 = Expression.Var(varY, eqType, Eff.Pure, loc)
      val default = Expression.Binary(BinaryOperator.Equal, exp1, exp2, Type.Bool, Eff.Pure, loc)

      /*
       * Match on the type to determine what equality expression to synthesize.
       */
      eqType match {
        case Type.Unit => default
        case Type.Bool => default
        case Type.Char => default
        case Type.Float32 => default
        case Type.Float64 => default
        case Type.Int8 => default
        case Type.Int16 => default
        case Type.Int32 => default
        case Type.Int64 => default
        case Type.BigInt => default
        case Type.Str => default

        case _ =>
          //
          // Enum Case.
          //
          if (eqType.isEnum) {
            //
            // Assume we have an enum:
            //
            //   enum Option[Int] {
            //     case None,
            //     case Some(Int)
            //    }
            //
            // then we generate the expression:
            //
            //   match (e1, e2) with {
            //     case (None(freshX), None(freshY)) => recurse(freshX, freshY)
            //     case (Some(freshX), Some(freshY)) => recurse(freshX, freshY)
            //     case _                            => false
            //   }
            //
            // where recurse is a recursive call to this procedure.
            //

            // Retrieve the enum symbol and enum.
            val enumSym = getEnumSym(eqType)
            val enumDecl = root.enums(enumSym)

            // The pair (e1, e2) to match against.
            val matchValue = Expression.Tuple(List(exp1, exp2), Type.mkTuple(eqType, eqType), Eff.Pure, loc)

            // Compute the cases specialized to the current type.
            val cases = casesOf(enumDecl, eqType)

            // Generate a match rule for each variant.
            val rs = cases map {
              case (tag, caseType) =>
                // Generate a case of the form:
                // (Constructor(freshX), Constructor(freshY)) => recurse(freshX, freshY)

                // Generate two fresh variable symbols.
                val freshX = Symbol.freshVarSym("x")
                val freshY = Symbol.freshVarSym("y")

                // Generate the two constructor patterns.
                val patX = Pattern.Tag(enumSym, tag, Pattern.Var(freshX, caseType, loc), eqType, loc)
                val patY = Pattern.Tag(enumSym, tag, Pattern.Var(freshY, caseType, loc), eqType, loc)

                // Generate the pattern.
                val p = Pattern.Tuple(List(patX, patY), Type.mkTuple(eqType, eqType), loc)

                // Generate the guard (simply true).
                val g = Expression.True(loc)

                // Generate the rule body.
                val expX = Expression.Var(freshX, caseType, Eff.Pure, loc)
                val expY = Expression.Var(freshY, caseType, Eff.Pure, loc)
                val b = mkApplyEq(expX, expY)

                // Put everything together.
                MatchRule(p, g, b)
            }

            // Generate a default rule to return false, if the constructors are mismatched.
            val p = Pattern.Wild(eqType, loc)
            val g = Expression.True(loc)
            val b = Expression.False(loc)
            val default = MatchRule(p, g, b)

            return Expression.Match(matchValue, rs ::: default :: Nil, Type.Bool, Eff.Pure, loc)
          }

          //
          // Tuple Case.
          //
          if (eqType.isTuple) {
            //
            // Assume we have a tuple (a, b, c)
            //
            // then we generate the expression:
            //
            //   match (e1, e2) with {
            //     case ((x1, x2, x3), (y1, y2, y3)) => recurse(x1, y1) &&  recurse(x2, y2) recurse(x2, y2)
            //   }
            //
            // where recurse is a recursive call to this procedure.
            //

            // The types of the tuple elements.
            val elementTypes = getElementTypes(eqType)

            // The pair (e1, e2) to match against.
            val matchValue = Expression.Tuple(List(exp1, exp2), Type.mkTuple(eqType, eqType), Eff.Pure, loc)

            // Introduce fresh variables for each component of the first tuple.
            val freshVarsX = (0 to getArity(eqType)).map(_ => Symbol.freshVarSym("x")).toList

            // Introduce fresh variables for each component of the second tuple.
            val freshVarsY = (0 to getArity(eqType)).map(_ => Symbol.freshVarSym("y")).toList

            // Compute the components of the singular rule.

            // The pattern of the rule.
            val xs = Pattern.Tuple((freshVarsX zip elementTypes).map {
              case (freshVar, tpe) => Pattern.Var(freshVar, tpe, loc)
            }, eqType, loc)
            val ys = Pattern.Tuple((freshVarsY zip elementTypes).map {
              case (freshVar, tpe) => Pattern.Var(freshVar, tpe, loc)
            }, eqType, loc)
            val p = Pattern.Tuple(List(xs, ys), Type.mkTuple(eqType, eqType), loc)

            // The guard of the rule.
            val g = Expression.True(loc)

            // The body of the rule.
            val b = (freshVarsX zip freshVarsY zip elementTypes).foldRight(Expression.True(loc): Expression) {
              case (((freshX, freshY), elementType), eacc) =>
                val expX = Expression.Var(freshX, elementType, Eff.Pure, loc)
                val expY = Expression.Var(freshY, elementType, Eff.Pure, loc)

                val e1 = mkApplyEq(expX, expY)
                val e2 = eacc
                Expression.Binary(BinaryOperator.LogicalAnd, e1, e2, Type.Bool, Eff.Pure, loc)
            }

            // Put the components together.
            val rule = MatchRule(p, g, b)

            // Return a match expression with the singular rule.
            return Expression.Match(matchValue, rule :: Nil, Type.Bool, Eff.Pure, loc)
          }

          throw InternalCompilerException(s"Unknown type '$eqType'.")
      }
    }

    // TODO: DOC
    def mkApplyToString(exp0: Expression): Expression = {
      val tpe = exp0.tpe

      // Construct the symbol of the toString definition.
      val sym = getOrMkToString(tpe)

      // Construct a call to the symbol passing the argument `exp`.
      val e = Expression.Def(sym, Type.mkArrow(List(tpe), Type.Str), Eff.Pure, sl)
      val args = exp0 :: Nil
      Expression.Apply(e, args, Type.Str, Eff.Pure, sl)
    }

    // TODO
    def getOrMkToString(tpe: Type): Symbol.DefnSym = mutToStringOps.getOrElse(tpe, {
      // Introduce a fresh symbol for the toString operator.
      val sym = Symbol.freshDefnSym("toString")

      // Immediately update the toString map.
      // We must do this to support toString operators on recursive
      // data structures where equality is defined inductively.
      mutToStringOps += (tpe -> sym)

      // Construct one fresh variable symbols for the formal parameter.
      val freshX = Symbol.freshVarSym("x")

      // Construct the formal parameter.
      val paramX = FormalParam(freshX, Ast.Modifiers.Empty, tpe, sl)

      // Annotations and modifiers.
      val ann = Ast.Annotations.Empty
      val mod = Ast.Modifiers.Empty

      // Type and formal parameters.
      val tparams = Nil
      val fparams = paramX :: Nil

      // The body expression.
      val exp = mkToStringExp(tpe, freshX)

      // The definition type.
      val lambdaType = Type.mkArrow(List(tpe), Type.Str)

      // Assemble the definition.
      val defn = Def(None, ann, mod, sym, tparams, fparams, exp, lambdaType, Eff.Pure, sl)

      // Add it to the map of new definitions.
      newDefs += (defn.sym -> defn)

      // And return its symbol.
      defn.sym
    })

    // TODO: DOC
    def mkToStringExp(tpe: Type, varX: Symbol.VarSym): Expression = {


      /*
       * The source location used for the generated expressions.
       */
      // TODO
      val loc = SourceLocation.Unknown

      val exp0 = Expression.Var(varX, tpe, Eff.Pure, loc)

      tpe match {
        case Type.Unit =>
          Expression.Str("()", loc)

        case Type.Bool =>
          val method = classOf[java.lang.Boolean].getMethod("toString", classOf[Boolean])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Char =>
          val method = classOf[java.lang.Character].getMethod("toString", classOf[Char])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Float32 =>
          val method = classOf[java.lang.Float].getMethod("toString", classOf[Float])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Float64 =>
          val method = classOf[java.lang.Double].getMethod("toString", classOf[Double])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Int8 =>
          val method = classOf[java.lang.Byte].getMethod("toString", classOf[Byte])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Int16 =>
          val method = classOf[java.lang.Short].getMethod("toString", classOf[Short])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Int32 =>
          val method = classOf[java.lang.Integer].getMethod("toString", classOf[Int])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Int64 =>
          val method = classOf[java.lang.Long].getMethod("toString", classOf[Long])
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.BigInt =>
          val method = classOf[java.math.BigInteger].getMethod("toString")
          Expression.NativeMethod(method, List(exp0), Type.Str, Eff.Pure, loc)

        case Type.Str => exp0

        case _ =>
          //
          // Enum case.
          //
          if (tpe.isEnum) {
            //
            // Assume we have an enum:
            //
            //   enum Option[Int] {
            //     case None,
            //     case Some(Int)
            //    }
            //
            // then we generate the expression:
            //
            //   match e with {
            //     case (None(freshX)) => "None(" + recurse(freshX) + ")"
            //     case (Some(freshX)) => "Some(" + recurse(freshX) + ")"
            //   }
            //
            // where recurse is a recursive call to this procedure.
            //
            // Retrieve the enum symbol and enum.
            val enumSym = getEnumSym(tpe)
            val enumDecl = root.enums(enumSym)

            // The expression `exp0` to match against.
            val matchValue = exp0

            // Compute the cases specialized to the current type.
            val cases = casesOf(enumDecl, tpe)

            // Generate a match rule for each variant.
            val rs = cases map {
              case (tag, caseType) =>
                // Generate a case of the form:
                // (Tag(freshX)) => "Tag(" + recurse(freshX) + ")"

                // Generate a fresh variable symbols.
                val freshX = Symbol.freshVarSym("x")

                // Generate a tag pattern.
                val p = Pattern.Tag(enumSym, tag, Pattern.Var(freshX, caseType, loc), tpe, loc)

                // Generate the guard (simply true).
                val g = Expression.True(loc)

                // Generate the rule body.
                val b = concatAll(List(
                  Expression.Str(tag, loc),
                  Expression.Str("(", loc),
                  mkApplyToString(Expression.Var(freshX, caseType, Eff.Pure, loc)),
                  Expression.Str(")", loc),
                ))

                // Put everything together.
                MatchRule(p, g, b)
            }

            return Expression.Match(matchValue, rs, Type.Str, Eff.Pure, loc)
          }

          // TODO: Rename Constructor to Tag.

          //
          // Tuple case.
          //
          if (tpe.isTuple) {
            //
            // Assume we have a tuple (a, b, c)
            //
            // then we generate the expression:
            //
            //   match exp0 with {
            //     case (x1, x2, x3) => "(" + recurse(x1) + ", " + recurse(x2) + ", " + recurse(x3) + ")"
            //   }
            //
            // where recurse is a recursive call to this procedure.
            //

            // The types of the tuple elements.
            val elementTypes = getElementTypes(tpe)

            // The expression `exp0` to match against.
            val matchValue = exp0

            // Introduce fresh variables for each component of the tuple.
            val freshVarsX = (0 to getArity(tpe)).map(_ => Symbol.freshVarSym("x")).toList

            // Compute the components of the singular rule.

            // The pattern of the rule.
            val p = Pattern.Tuple((freshVarsX zip elementTypes).map {
              case (freshVar, elmType) => Pattern.Var(freshVar, elmType, loc)
            }, tpe, loc)

            // The guard of the rule.
            val g = Expression.True(loc)

            // The elements of the tuple.
            val inner = (freshVarsX zip elementTypes).map {
              case (freshX, elementType) => mkApplyToString(Expression.Var(freshX, elementType, Eff.Pure, loc))
            }

            // Construct the string expression (e1, e2, e3, ...)
            val b = concatAll(
              Expression.Str("(", loc) ::
                intersperse(inner, Expression.Str(", ", loc)) :::
                Expression.Str(")", loc) :: Nil
            )

            // Put the components together.
            val rule = MatchRule(p, g, b)

            // Return a match expression with the singular rule.
            return Expression.Match(matchValue, rule :: Nil, Type.Str, Eff.Pure, loc)
          }

          throw InternalCompilerException(s"Unknown type '$tpe'.")
      }
    }

    /**
      * Returns the enum symbol of the given enum type `tpe`.
      */
    def getEnumSym(tpe: Type): Symbol.EnumSym = tpe match {
      case Type.Enum(sym, kind) => sym
      case Type.Apply(inner, _) => getEnumSym(inner)
      case _ => throw InternalCompilerException(s"The given type '$tpe' is not an enum type.")
    }

    /**
      * Returns the tuple arity of the given type `tpe`.
      */
    def getArity(tpe: Type): Int = getElementTypes(tpe).length

    /**
      * Returns the element types of the given tuple type `tpe`.
      */
    def getElementTypes(tpe: Type): List[Type] = tpe match {
      case Type.Apply(Type.FTuple(l), ts) => ts
      case _ => throw InternalCompilerException(s"The given type '$tpe' is not a tuple type.")
    }

    /**
      * Returns an association list of the (tag, type)s of the given `enum` specialized to the given type `tpe`.
      */
    def casesOf(enum: Enum, tpe: Type): List[(String, Type)] = {
      // Compute a substitution for the parametric enum specialized to the specific type.
      val subst = Unification.unify(enum.tpe, tpe).get

      // Apply the substitution to each case.
      enum.cases.map {
        case (tag, Case(_, _, caseType)) =>
          tag -> subst(caseType)
      }.toList
    }

    /**
      * Returns an expression that is the string concatenation of `exp1` and `exp2`.
      */
    def concat(exp1: Expression, exp2: Expression): Expression =
      Expression.Binary(BinaryOperator.Plus, exp1, exp2, Type.Str, Eff.Pure, SourceLocation.Unknown)

    /**
      * Returns an expression that is the string concatenation of the given expressions `exps`.
      */
    def concatAll(exps: List[Expression]): Expression =
      exps.foldLeft(Expression.Str("", SourceLocation.Unknown): Expression)(concat)

    /**
      * Inserts the element `a` between every element of `l`.
      */
    def intersperse[A](l: List[A], a: A): List[A] = l match {
      case Nil => Nil
      case x :: Nil => x :: Nil
      case x :: y :: xs => x :: a :: intersperse(y :: xs, a)
    }

    ///////////////////////////////////////////////////////////////////////////
    /// Main body of `run`                                                  ///
    ///////////////////////////////////////////////////////////////////////////

    /*
     * Every type that appears as return type of some definition.
     */
    val typesInDefs: Set[Type] = root.defs.map {
      case (_, Def(_, _, _, _, _, _, exp, _, _, _)) => exp.tpe
    }.toSet

    /*
     * Every type that appears as an attribute in some table.
     */
    val typesInTables: Set[Type] = root.tables.flatMap {
      case (sym, Table.Relation(_, _, attributes, _)) => attributes.map {
        case Attribute(_, tpe, _) => tpe
      }
      case (sym, Table.Lattice(_, _, keys, value, _)) =>
        // TODO: Equality of value?
        keys.map {
          case Attribute(_, tpe, _) => tpe
        }
    }.toSet

    /*
     * Rewrite every equality expression in a definition to explicitly call the equality operator.
     */
    val defs = root.defs.map {
      case (sym, defn) => sym -> visitDef(defn)
    }

    /*
     * Introduce an Equality operator for every attribute of a table.
     */
    val equalityOps = typesInTables.foldLeft(Map.empty[Type, Symbol.DefnSym]) {
      case (macc, tpe) => macc + (tpe -> getOrMkEq(tpe))
    }

    /*
     * Introduce a ToString operator for every return type of attribute of a table.
     */
    val toStringOps = (typesInDefs ++ typesInTables).foldLeft(Map.empty[Type, Symbol.DefnSym]) {
      case (macc, tpe) => macc
      case (macc, tpe) if tpe.isArrow => macc + (tpe -> getOrMkToString(tpe))
    }

    /*
     * Construct the map of special operators.
     */
    val specialOps: Map[SpecialOperator, Map[Type, Symbol.DefnSym]] = Map(
      SpecialOperator.Equality -> equalityOps,
      SpecialOperator.ToString -> toStringOps
    )

    // TODO: Timings

    root.copy(defs = defs ++ newDefs, specialOps = specialOps).toSuccess

  }

}
