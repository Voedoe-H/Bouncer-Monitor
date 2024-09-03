package SymbolicSimulation

import com.github.tukcps.aadd.*
import Misc.*

/**
 * Data class that consists of the starting state information of the sigma-delta modulator as well as the final results of the sigma-delta modulator post the symbolic simulation.
 * The different AADDs and BDD^as are ordered to string identifier of which variable they represent.
 * */
data class benchmarkResults(val prestateAffineForms:MutableMap<String,DD<*>>,
                            val postState:MutableMap<String,DD<*>>,
                            val preStateDisc: MutableMap<String,DD<*>>,
                            val postStateDisc: MutableMap<String,DD<*>>)


/**
 * Symbolic simulation of a sigma-delta modular of order 3. The moc is equivalent to that of the numerical simulation and correlates to the
 * hybrid automata model presented in "Verification of Analog and Mixed-Signal Circuits Using Hybrid System Techniques" by Dang et al..
 * @param builder: context object that keeps track of all the path constraints as well as all the noise symbols introduced in the context of the symbolic simulation
 * @return data class object that saves the initial state of the symbolic simulation and the state after the symbolic simulation
 * */
fun sigmaDeltaSymbolicSimulation(builder:DDBuilder):benchmarkResults
{
    with(builder){

        /** Tracking: */
        val x0Min = mutableListOf<Double>()
        val x0Max = mutableListOf<Double>()

        val x1Min = mutableListOf<Double>()
        val x1Max = mutableListOf<Double>()

        val x2Min = mutableListOf<Double>()
        val x2Max = mutableListOf<Double>()

        val x0SignalSet = mutableListOf<AADD>()
        val x1SignalSet = mutableListOf<AADD>()
        val x2SignalSet = mutableListOf<AADD>()

        val discretestate = mutableListOf<BDD>()

        /** Simulation setup: */
        val iterations = 1

        /** System Params: */
        val a1:AADD = range(0.0444 - 0.01 .. 0.0444 + 0.01) // 0.0444 +- 0.01
        val a2:AADD = range(0.2881 - 0.1 .. 0.2881 + 0.1) // 0.2881 +- 0.1
        val a3:AADD = range(0.7997 - 0.1 .. 0.7997 + 0.1) // 0.7997 +- 0.1


        val b1:AADD = range(0.0444 - 0.01 .. 0.0444 + 0.01)
        val b2:AADD = range(0.2881 - 0.1 .. 0.2881 + 0.1)
        val b3:AADD = range(0.7997 - 0.1 .. 0.7997 + 0.1)


        var x0 = range(-1.6 .. 1.6)   //range(-0.01 .. 0.01)
        var x1 = range(-2.4 .. 2.4)   //-0.01 .. 0.01
        var x2 = range(-2.8 .. 2.8)  //-0.01 .. 0.01
        val prestateAffineForms = mutableMapOf<String,DD<*>>()
        val preStateDisc = mutableMapOf<String,DD<*>>()

        prestateAffineForms["x0"] = x0.clone()
        prestateAffineForms["x1"] = x1.clone()
        prestateAffineForms["x2"] = x2.clone()


        //var pos : BDD = variable("init")
        var pos : BDD = variable("init")
        preStateDisc["d"] = pos.clone()

        /** Input: */
        val u = range(-0.5 .. 0.5) // range(-0.5 .. 0.5)
        /** Behaviour matrix setup: */
        val matAPos = arrayOf(
            arrayOf(scalar(1.0),scalar(0.0),scalar(0.0)),
            arrayOf(scalar(1.0), scalar(1.0),scalar(0.0)),
            arrayOf(scalar(0.0),scalar(1.0),scalar(1.0))
        )

        val matBPos = arrayOf(b1,b2,b3)
        val constPos = arrayOf(-a1,-a2,-a3)

        val matANeg = arrayOf(
            arrayOf(scalar(1.0),scalar(0.0),scalar(0.0)),
            arrayOf(scalar(1.0), scalar(1.0),scalar(0.0)),
            arrayOf(scalar(0.0),scalar(1.0),scalar(1.0))
        )

        val matBNeg = arrayOf(b1,b2,b3)
        val constNeg = arrayOf(a1,a2,a3)

        x0SignalSet.add(x0.clone())
        x1SignalSet.add(x1.clone())
        x2SignalSet.add(x2.clone())
        x0Min.add(x0.min)
        x0Max.add(x0.max)
        x1Min.add(x1.min)
        x1Max.add(x1.max)
        x2Min.add(x2.min)
        x2Max.add(x2.max)
        discretestate.add(pos)

        for(i in 0..<iterations)
        {
            /** Continuous Update: */

            val x2p = x2.clone()

            IF(pos)
                val Axp = vecMatMulAffine(arrayOf(x0,x1,x2),matAPos,builder)
                val bup = scalarMulAffine(u,matBPos,builder)
                val sump = vecAddAffine(vecAddAffine(Axp,bup,builder),constPos,builder)
                x0 = assign(x0,sump[0])
                x1 = assign(x1,sump[1])
                x2 = assign(x2,sump[2])
            // getRange function computes the actual leaf values depending on the linear program defined by its path. This has to be called in the current AADD implementation to prevent over approximation
                x0.getRange()
                x1.getRange()
                x2.getRange()
            ELSE()
                val Axn = vecMatMulAffine(arrayOf(x0,x1,x2),matANeg,builder)
                val bun = scalarMulAffine(u,matBNeg,builder)
                val sumn = vecAddAffine(vecAddAffine(Axn,bun,builder),constNeg,builder)
                x0 = assign(x0,sumn[0])
                x1 = assign(x1,sumn[1])
                x2 = assign(x2,sumn[2])
                x0.getRange()
                x1.getRange()
                x2.getRange()
            END()

            /** Discrete Update: */
            val condinp = x2+u
            IF(pos)
                IF(condinp.lessThanOrEquals(0.0))
                    pos = assign(pos,False)
                END()
            ELSE()
                IF(condinp.greaterThanOrEquals(0.0))
                    pos = assign(pos,True)
                END()
            END()

            x0SignalSet.add(x0.clone())
            x1SignalSet.add(x1.clone())
            x2SignalSet.add(x2.clone())
            x0Min.add(x0.min)
            x0Max.add(x0.max)
            x1Min.add(x1.min)
            x1Max.add(x1.max)
            x2Min.add(x2.min)
            x2Max.add(x2.max)
            discretestate.add(pos.clone())
        }
        val signalSets = mutableListOf<MutableList<AADD>>()
        signalSets.add(x0SignalSet)
        signalSets.add(x1SignalSet)
        signalSets.add(x2SignalSet)

        val postState = mutableMapOf<String,DD<*>>()

        postState["x0"] = x0SignalSet[1].clone()
        postState["x1"] = x1SignalSet[1].clone()
        postState["x2"] = x2SignalSet[1].clone()

        val postStateDisc = mutableMapOf<String,DD<*>>()

        postStateDisc["d"] = discretestate[1].clone()

        val results = benchmarkResults(prestateAffineForms,postState,preStateDisc,postStateDisc)

        return results
    }
}
