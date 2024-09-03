package BouncerMonitor

import SimplexSolver.*
import com.github.tukcps.aadd.*
import com.github.tukcps.aadd.values.AffineForm
import com.github.tukcps.aadd.values.StateTuple
import java.util.*


data class InequalitySystem(val preStateAffineForms: MutableMap<String,AffineForm>, val postStateAffineForms: MutableMap<String,AffineForm>, val pathConstraints: MutableList<Int>)

class MinimalBouncerMonitor(private val builder: DDBuilder, private val preState : MutableMap<String, DD<*>>, private val postState:MutableMap<String, DD<*>>, private val delta: Double)
{
    // The computed full dependency tree of the post state AADDs / BDDs
    private val postStateCDD : CDD = builder.generateCDD(postState, StateTuple(builder),builder)
    // The inequation systems that are checked inside of the algorithm
    private val inequalitySystems = mutableListOf<InequalitySystem>()
    private val monitoredVariables = mutableListOf<String>()
    private val monitoredContinuousVariables = mutableListOf<String>()
    private val monitoredDiscreteVariables = mutableListOf<String>()

    // All the lp variables corresponding to all the existing noise symbols
    private val lpVariables = mutableMapOf<Int,LpVariable>()
    private val noiseVariableConstraints = mutableListOf<LpConstraint>()
    private val lpPathConstraints = mutableMapOf<Int,LpConstraint>()

    init
    {
        /** Input inconsistency checks: */
        if(preState.keys != postState.keys) throw BouncerConfigException("Pre and Post State variables don't match")
        monitoredVariables.addAll(preState.keys)

        for(variable in preState)
        {
            if(variable.value is AADD)monitoredContinuousVariables.add(variable.key)
            else monitoredDiscreteVariables.add(variable.key)
        }
        //println("post state cdd symbolic: ${postStateCDD.toSymbolicString()}")
        computeInequalitySystems()
        determineAllLpVariables()
        createLpVariableConstraints()
        createLpPathConstraints()
    }

    /**
     * Function computing all the different possible inequality system cases that are then evaluated inside the actual algorithm
     * */
    private fun computeInequalitySystems()
    {
        val leafsWithPaths = postStateCDD.gatherLeafsWithPaths()

        // Determine all the affine forms together with the id to which continuous variable of the hybrid system they belong of the pre-state
        val preStateAffineForms = mutableMapOf<String, AffineForm>()

        for(variable in preState)
        {
            if(variable.value is AADD) preStateAffineForms[variable.key] = (variable.value as AADD.Leaf).value
        }

        for((i,leafs) in leafsWithPaths.withIndex())
        {
            val postStateAffineForms = mutableMapOf<String, AffineForm>()
            for(cond in leafs.second)
            {
                if(cond>=0)
                {
                    val condition = builder.conds.getCondition(cond)
                    //if(condition is BDD)println("Source State:${true}")
                }
                else
                {
                    val condition = builder.conds.getCondition(-cond)
                    //if(condition is BDD)println("Source State:${false}")
                }
            }
            for(forms in leafs.first.value.getContinuousValues())
            {
                postStateAffineForms[forms.key] = forms.value
            }

            val ineq = InequalitySystem(preStateAffineForms.toMutableMap(),postStateAffineForms.toMutableMap(),leafs.second)
            inequalitySystems.add(ineq)
        }
    }

    /**
     * The Variables generally in the inequality systems created in the verification context are the noise symbols of the affine forms.
     * This function determines all the noise symbols that exist in the context of the supplied affine forms, AADDs and BDDAs
     * */
    private fun determineAllLpVariables()
    {
        val symbols = TreeSet<Int>()
        for(inequalitySystem in inequalitySystems)
        {
            // Find all the noise symbols that exist in the affine forms defining the pre-state
            for(affineForm in inequalitySystem.preStateAffineForms)
            {
                for(noiseSymbol in affineForm.value.xi)symbols.add(noiseSymbol.key)
            }

            // Find all the noise symbols that exist in the affine forms defining the post-state
            for(affineForm in inequalitySystem.postStateAffineForms)
            {
                for(noiseSymbol in affineForm.value.xi)symbols.add(noiseSymbol.key)
            }

            // Find all the noise symbols that exist in the affine forms that define the linear constraints applying to the post/pre-state affine forms
            for(constraintID in inequalitySystem.pathConstraints)
            {
                var realConstraintID = constraintID
                // Need to negate since in the pathConstraints the ids of the constraints are negative if the path goes onto the false branch
                if(constraintID<0) realConstraintID *= -1
                val conditionAffineForm = builder.conds.getCondition(realConstraintID)
                if(conditionAffineForm is AADD.Leaf)
                {
                    for(noiseSymbol in conditionAffineForm.value.xi) symbols.add(noiseSymbol.key)
                }
            }
        }
        // Create for all the found noise symbols the LP Variable objects used in the LP Problems in the actual runtime verification algorithm
        for(noiseVariable in symbols) lpVariables[noiseVariable] = LpVariable("$noiseVariable",true)
    }

    /**
     * Function that creates all the LP Variable constraints -1 <= e <= 1 for all the noise variables that are added in lpVariables list. The constraints are saved
     * in the noiseVariableConstraints list. Since these constraints are used for all the inequality systems defined in the verification context we can create them once
     * globally to utilise them in any LP problem.
     * */
    private fun createLpVariableConstraints()
    {
        for(variable in lpVariables)
        {
            val lowerConstraint = LpConstraint(LpExpression(mapOf(variable.value to 1.0)),LpConstraintSign.GREATER_OR_EQUAL,-1.0)
            val upperConstraint = LpConstraint(LpExpression(mapOf(variable.value to 1.0)),LpConstraintSign.LESS_OR_EQUAL,1.0)
            noiseVariableConstraints.add(lowerConstraint)
            noiseVariableConstraints.add(upperConstraint)
        }
    }

    /**
     * The path constraints are independent on the measured values. Thus, we can create them already and then use them inside the verification algorithm just based
     * on the index of the constraint at its sign.
     * */
    private fun createLpPathConstraints()
    {
        for(inequalitySystem in inequalitySystems)
        {
            for(constraintID in inequalitySystem.pathConstraints)
            {
                if(!lpPathConstraints.contains(constraintID))
                {
                    var realConstraintID = constraintID
                    // Required if we have the negation of the constraint then the ID is tagged with a negative sign
                    if(realConstraintID<0)realConstraintID*=-1
                    val constraintAffineFormRepresentation = builder.conds.getConstraint(realConstraintID)
                    // Check if we have a reel valued constraint or simply a decision variable
                    // If it is a decision variable we can ignore this case here at this point but need to take it into consideration if we have the fully observed mode
                    if(constraintAffineFormRepresentation is AADD.Leaf)
                    {
                        // Mapping between the LPVariables and the reel coefficients of these variables in the constraint
                        val coefficientVarMap = mutableMapOf<LpVariable,Double>()

                        for(symbol in constraintAffineFormRepresentation.value.xi)
                        {
                            coefficientVarMap[lpVariables[symbol.key]!!] = symbol.value
                        }

                        if(constraintID>0)
                        {
                            val pathConstraint = LpConstraint(LpExpression(coefficientVarMap),LpConstraintSign.GREATER_OR_EQUAL,
                                -constraintAffineFormRepresentation.value.central - constraintAffineFormRepresentation.value.r)
                            lpPathConstraints[constraintID] = pathConstraint
                        }
                        else
                        {
                            val pathConstraint = LpConstraint(LpExpression(coefficientVarMap),LpConstraintSign.LESS_OR_EQUAL,
                                -constraintAffineFormRepresentation.value.central - constraintAffineFormRepresentation.value.r)
                            lpPathConstraints[constraintID] = pathConstraint
                        }
                    }
                }
            }
        }
    }

    /**
     *
     * */
    fun evaluateTransitionOnlyContinuous(observedContinuousTransition: MutableMap<String, Pair<Double, Double>>,debug: Boolean = false) : Boolean
    {
        var verificationResult = false
        for(inequalitySystem in inequalitySystems)
        {
            if(isInequalitySystemSolvable(inequalitySystem,observedContinuousTransition,debug))
            {
                verificationResult = true
                break
            }
        }
        return verificationResult
    }

    fun isInequalitySystemSolvable(inequalitySystem: InequalitySystem, observedContinuousTransition: MutableMap<String, Pair<Double, Double>>,debug:Boolean = false) : Boolean
    {
        // Create Lp Builder that will create the LP Problem
        val lpBuilder = LpProblemBuilder()
        // Just add all the existing noise symbols to the builder
        for (variable in lpVariables.values.toList())lpBuilder.addVariable(variable)
        // Add the constraints that constrain the noise variables to the range -1 to 1
        for (constraint in noiseVariableConstraints) lpBuilder.addConstraint(constraint)
        // Add the constraint that is in the path
        for(pathId in inequalitySystem.pathConstraints)
        {
            if(pathId in lpPathConstraints.keys) lpBuilder.addConstraint(lpPathConstraints[pathId]!!)
        }
        // create the value constraints of the time step t
        for(variable in inequalitySystem.preStateAffineForms)
        {
            val correspondingMeasurementValue = observedContinuousTransition[variable.key]!!.first
            val correspondingAffineForm = variable.value
            val continuousDynamicConstraints = generateLpConstraintsForAffineForm(correspondingAffineForm,correspondingMeasurementValue)
            lpBuilder.addConstraint(continuousDynamicConstraints.first)
            lpBuilder.addConstraint(continuousDynamicConstraints.second)
        }
        // create the value constraints of the time step t+1
        for(variable in inequalitySystem.postStateAffineForms)
        {
            val correspondingMeasurementValue = observedContinuousTransition[variable.key]!!.second
            val correspondingAffineForm = variable.value
            val continuousDynamicsConstraint = generateLpConstraintsForAffineForm(correspondingAffineForm,correspondingMeasurementValue)
            lpBuilder.addConstraint(continuousDynamicsConstraint.first)
            lpBuilder.addConstraint(continuousDynamicsConstraint.second)
        }
        // Just create constant LP function
        lpBuilder.function = LpFunction(LpExpression(mutableMapOf<LpVariable,Double>(),1.0),LpFunctionOptimization.MAXIMIZE)
        val problem = lpBuilder.build()
        if(debug)
        {
            println("LP Problem By Monitor:")
            problem.printProblem()
            println(" ")
        }
        return try
        {

            val solution = solve(problem)

            if(solution is Solved)
            {
                if(debug)println("Has Solution:${solution.variablesValues}")
                return true
            }
            else if(solution is Unbounded)
            {
                if(debug)println("Has Unbounded Solution")
                return false
            }
            else if(solution is NoSolution)
            {
                if(debug)println("Has No Solution")
                return false
            }
            else return false
        }
        catch(e:NoSolutionException)
        {
            false
        }
        catch (e:UnboundedException)
        {
            false
        }
        catch (e:Exception)
        {
            println("Generalized Exception")
            println(e)
            false
        }
    }

    /**
     * Function that computes for a given affine form the LpConstraint that only allows real values of that affine form that are equal to the measurement value +-Delta
     * @param af: The affine form the constraints should bound
     * @param measurementValue: The measurement value the affine form should be bound on
     * @return The first LpConstraint is the lower bound and the second the upper bound
     * */
    private fun generateLpConstraintsForAffineForm(af: AffineForm, measurementValue:Double) : Pair<LpConstraint,LpConstraint>
    {
        val constraintCoefficients = mutableMapOf<LpVariable,Double>()
        for(symbol in af.xi)
        {
            constraintCoefficients[lpVariables[symbol.key]!!] = symbol.value
        }
        val lowerConstraint = LpConstraint(LpExpression(constraintCoefficients),LpConstraintSign.GREATER_OR_EQUAL,
            measurementValue-delta - af.central - af.r)
        val upperConstraint = LpConstraint(LpExpression(constraintCoefficients),LpConstraintSign.LESS_OR_EQUAL,
            measurementValue+delta - af.central + af.r )
        return Pair(lowerConstraint,upperConstraint)
    }


    /**
     * Utility Functions
     * */

    /**
     * @return The number of inequality systems that are defined by the CDD
     * */
    fun getNumberOfInequalitySystems() : Int
    {
        return inequalitySystems.size
    }

    /**
     * Function printing to console the affine forms and decision variables defining the prestate and the cdd defining the post state
     * */
    fun printBOUNCERSetup()
    {
        println("Monitored Continuous Variables: ${monitoredContinuousVariables}")
        println("Monitored Discrete Variables:${monitoredDiscreteVariables}")
        println("BEGIN: Pre State")
        for(af in preState)
        {
            println("Variable ${af.key}: ${af.value}")
        }

        println("BEGIN: Post State")

        println(postStateCDD.toIteString())

        println("By Pre and Post State defined number of inequality systems:${inequalitySystems.size}")
        println("Number of LP Variables created:${lpVariables.size}")
        println("LpVariables:$lpVariables")
        println("#LPVariable Constraints:${noiseVariableConstraints.size}")
        println("#Path Constraints:${lpPathConstraints.size}")
    }

    fun symbolicConstraintRepresentationPrint(af: AffineForm, direction:Boolean)
    {
        var str = ""

        for(symbol in af.xi)
        {
            str = "${symbol.value}e_${symbol.key}+" + str
        }
        str = str.dropLast(1)
        str += if(direction) {
            "<=${af.central}"
        } else {
            ">=${af.central}"
        }
        println(str)
    }

    fun symbolicAffineFormPrint(af: AffineForm)
    {
        var str="${af.central}+"
        for(symbol in af.xi)
        {
            str+="${symbol.value}e_${symbol.key}+"
        }
        str = str.dropLast(1)
        println(str)
    }

    fun printContext()
    {
        for((i,ineq) in inequalitySystems.withIndex())
        {
            println("Possible World $i Start:")
            println("Start Constraints:")
            for(constraint in ineq.pathConstraints)
            {
                if(constraint>=0)
                {
                    val bconstraint = builder.conds.getCondition(constraint)!!
                    if(bconstraint is AADD.Leaf)
                    {
                        val af = bconstraint.value
                        symbolicConstraintRepresentationPrint(af,true)
                    }
                }
                else
                {
                    val bconstraint = builder.conds.getCondition(-constraint)!!
                    if(bconstraint is AADD.Leaf)
                    {
                        val af = bconstraint.value
                        symbolicConstraintRepresentationPrint(af,false)
                    }
                }
            }
            println("Start State")
            for(state in ineq.preStateAffineForms)
            {
                print("${state.key}: ")
                symbolicAffineFormPrint(state.value)
            }
            println("Stop State")
            for(state in ineq.postStateAffineForms)
            {
                print("${state.key}: ")
                symbolicAffineFormPrint(state.value)
            }
            println("Possible World Stop")
        }
    }

    fun printPathConstraints()
    {
        for(constraint in lpPathConstraints)
        {
            println(constraint.key)
            println("${constraint.value.expression} ${constraint.value.sign} ${constraint.value.constantValue}")
            println("${constraint.value.expression.terms}")

        }
    }

    fun getInequalitySystems(): MutableList<InequalitySystem> {
        return inequalitySystems
    }

}