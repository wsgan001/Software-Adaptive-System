package jmetal.problems;

import jmetal.core.Problem;
import jmetal.core.SolutionType;
import jmetal.core.Variable;
import jmetal.encodings.solutionType.IntSolutionType;
import jmetal.encodings.variable.Int;
import jmetal.util.JMException;
import jmetal.util.PseudoRandom;

public class SASSolutionType extends IntSolutionType {
	/**
	 * Constructor
	 * @param problem
	 * @throws ClassNotFoundException 
	 */
	public SASSolutionType(Problem problem) throws ClassNotFoundException {
		super(problem) ;
	} // Constructor

	/**
	 * Creates the variables of the solution
	 * @param decisionVariables
	 */
	public Variable[] createVariables() {
		
		Variable[] variables = new Variable[problem_.getNumberOfVariables()];
		SASSolution sol = (SASSolution)((SAS)problem_).factory.getSolution(problem_, variables);
		for (int var = 0; var < problem_.getNumberOfVariables(); var++)
			try {
				variables[var] = new Int((int) (PseudoRandom.randInt(
						sol.getLowerBoundforVariable(var),
						sol.getUpperBoundforVariable(var))),
						(int)problem_.getLowerLimitSAS(var), (int)problem_.getUpperLimitSAS(var));
			} catch (JMException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		return variables ;
	} // createVariables

}
