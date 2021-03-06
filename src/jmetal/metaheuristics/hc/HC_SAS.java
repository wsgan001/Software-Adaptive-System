//  NSGAII.java
//
//  Author:
//       Antonio J. Nebro <antonio@lcc.uma.es>
//       Juan J. Durillo <durillo@lcc.uma.es>
//
//  Copyright (c) 2011 Antonio J. Nebro, Juan J. Durillo
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU Lesser General Public License for more details.
// 
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jmetal.metaheuristics.hc;

import org.femosaa.core.SASAlgorithmAdaptor;
import org.femosaa.core.SASSolution;
import org.femosaa.core.SASSolutionInstantiator;

import jmetal.core.*;
import jmetal.util.comparators.CrowdingComparator;
import jmetal.util.*;

/**
 * 
 * @author keli, taochen
 *
 */
public class HC_SAS extends Algorithm {

	private SASSolutionInstantiator factory = null;
	

	// ideal point
	double[] z_;

	// nadir point
	double[] nz_;
	
	int populationSize_;
	
	SolutionSet population_;
	/**
	 * Constructor
	 * @param problem Problem to solve
	 */
	public HC_SAS(Problem problem) {
		super (problem) ;
	} // NSGAII


  	/**
  	 * Constructor
  	 * @param problem Problem to solve
  	 */
	public HC_SAS(Problem problem, SASSolutionInstantiator factory) {
		super(problem);
        this.factory = factory;
	}

	/**   
	 * Runs the NSGA-II algorithm.
	 * @return a <code>SolutionSet</code> that is a set of non dominated solutions
	 * as a result of the algorithm execution
	 * @throws JMException 
	 */
	public SolutionSet execute() throws JMException, ClassNotFoundException {
		
		if (factory == null) {
			throw new RuntimeException("No instance of SASSolutionInstantiator found!");
		}
		
		
		int maxEvaluations  = ((Integer) getInputParameter("maxEvaluations")).intValue();
		int evaluations;

		z_  = new double[problem_.getNumberOfObjectives()];
	    nz_ = new double[problem_.getNumberOfObjectives()];

		int populationSize = 2;
		//Initialize the variables
		SolutionSet population = new SolutionSet(populationSize);
		//SolutionSet nadir_population = new SolutionSet(populationSize);
		
		evaluations = 0;

	

		
		// Create the initial solutionSet
		Solution newSolution;
		for (int i = 0; i < 1; i++) {
			newSolution = factory.getSolution(problem_);
			problem_.evaluate(newSolution);
			problem_.evaluateConstraints(newSolution);
			evaluations++;
			population.add(newSolution);
			//nadir_population.add(newSolution);
		} //for       

		initIdealPoint();
		initNadirPoint();
		
		SolutionSet old_population = new SolutionSet(populationSize);
		if(SASAlgorithmAdaptor.isFuzzy) {
			old_population = population;
			population = factory.fuzzilize(population);
		}
		
		for (int i = 0; i < population.size(); i++) {
			fitnessAssignment(population.get(i));	// assign fitness value to each solution			
		}
		
		if (SASAlgorithmAdaptor.logGenerationOfObjectiveValue > 0) {
			org.femosaa.util.Logger.logSolutionSetWithGenerationAndFuzzyValue(population, old_population,
					"SolutionSetWithGen.rtf", 0);
		} 
		
		int index = 0;
		
		// Generations 
		while (evaluations < maxEvaluations) {

		
	
	

			/**
			 * This is a hill climbing search, where the neighbour means the next adjacent variable.
			 */
	

			Solution nextSolution = factory.getSolution(population.get(0));
			
			((SASSolution) nextSolution).mutateWithDependency(index,  true);
			index++;
			if(index >= nextSolution.getDecisionVariables().length) {
				index = 0;
			}
			
			
		
			
			problem_.evaluate(nextSolution);
			problem_.evaluateConstraints(nextSolution);
			
			updateReference(nextSolution);
			updateNadirPoint(nextSolution);
		
			
			old_population.add(nextSolution);
			if(SASAlgorithmAdaptor.isFuzzy) {
				population = factory.fuzzilize(old_population);
			}
			
			fitnessAssignment(population.get(0));
			fitnessAssignment(population.get(1));
			
			
			if(population.get(0).getFitness() < population.get(1).getFitness() ) {
				population.remove(1);
				old_population.remove(1);
				
			} else {
				population.remove(0);
				old_population.remove(0);
			}
			
			evaluations++;
			if(SASAlgorithmAdaptor.logGenerationOfObjectiveValue > 0 && evaluations%SASAlgorithmAdaptor.logGenerationOfObjectiveValue == 0) {
				org.femosaa.util.Logger.logSolutionSetWithGenerationAndFuzzyValue(population, old_population, "SolutionSetWithGen.rtf", 
						evaluations );
			}

		
		} // while

		if(SASAlgorithmAdaptor.isFuzzy) {
			population = old_population;
		}
		// Return as output parameter the required evaluations
		//setOutputParameter("evaluations", requiredEvaluations);
		
		
		
		return population;
	} // execute
	
	public SolutionSet doRanking(SolutionSet population){
		SolutionSet set = new SolutionSet(1);
		set.add(population.get(0));
		
		return set;
	}
	
	
	/**
	 * This is used to assign fitness value to a solution, according to weighted sum strategy.
	 * @param cur_solution
	 */
	public void fitnessAssignment(Solution cur_solution) {
		double cur_fitness = 0.0;
		double weight	   = 1.0 / (double) problem_.getNumberOfObjectives();

		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			cur_fitness += weight * (nz_[i] != z_[i]? ((cur_solution.getObjective(i) - z_[i]) / (nz_[i] - z_[i])) : 
				((cur_solution.getObjective(i) - z_[i]) / (nz_[i]))); 
		
		if(Double.isNaN(cur_fitness)) {
			System.out.print("Find one fitness with NaN!\n");
			cur_fitness = 0;//-1.0e+30;
		}
		cur_solution.setFitness(cur_fitness);
	}
	

  	/**
  	 * Initialize the ideal point
	 * @throws JMException
	 * @throws ClassNotFoundException
	 */
	void initIdealPoint() throws JMException, ClassNotFoundException {
		if(SASAlgorithmAdaptor.isFuzzy) {
			for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
				z_[i] = 0;
			}
			return;
		}
		
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			z_[i] = 1.0e+30;

		for (int i = 0; i < populationSize_; i++)
			updateReference(population_.get(i));
	}

	/**
	 * Initialize the nadir point
	 * @throws JMException
	 * @throws ClassNotFoundException
	 */
	void initNadirPoint() throws JMException, ClassNotFoundException {
		if(SASAlgorithmAdaptor.isFuzzy) {
			for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
				nz_[i] = 1;
			}
			return;
		}
		
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			nz_[i] = -1.0e+30;

		for (int i = 0; i < populationSize_; i++)
			updateNadirPoint(population_.get(i));
	}
	
   	/**
   	 * Update the ideal point, it is just an approximation with the best value for each objective
   	 * @param individual
   	 */
	void updateReference(Solution individual) {
		if(SASAlgorithmAdaptor.isFuzzy) {
			return;
		}
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
			if (individual.getObjective(i) < z_[i])
				z_[i] = individual.getObjective(i);
		}
	}
  
  	/**
  	 * Update the nadir point, it is just an approximation with worst value for each objective
  	 * 
  	 * @param individual
  	 */
	void updateNadirPoint(Solution individual) {
		if(SASAlgorithmAdaptor.isFuzzy) {
			return;
		}
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
			if (individual.getObjective(i) > nz_[i])
				nz_[i] = individual.getObjective(i);
		}
	}
	
	/**
	 * This is used to find the knee point from a set of solutions
	 * 
	 * @param population
	 * @return
	 */
//	public Solution kneeSelection(SolutionSet population_) {		
//		int[] max_idx    = new int[problem_.getNumberOfObjectives()];
//		double[] max_obj = new double[problem_.getNumberOfObjectives()];
//		int populationSize_ = population_.size();
//		// finding the extreme solution for f1
//		for (int i = 0; i < populationSize_; i++) {
//			for (int j = 0; j < problem_.getNumberOfObjectives(); j++) {
//				// search the extreme solution for f1
//				if (population_.get(i).getObjective(j) > max_obj[j]) {
//					max_idx[j] = i;
//					max_obj[j] = population_.get(i).getObjective(j);
//				}
//			}
//		}
//
//		if (max_idx[0] == max_idx[1])
//			System.out.println("Watch out! Two equal extreme solutions cannot happen!");
//		
//		int maxIdx;
//		double maxDist;
//		double temp1 = (population_.get(max_idx[1]).getObjective(0) - population_.get(max_idx[0]).getObjective(0)) * 
//				(population_.get(max_idx[0]).getObjective(1) - population_.get(0).getObjective(1)) - 
//				(population_.get(max_idx[0]).getObjective(0) - population_.get(0).getObjective(0)) * 
//				(population_.get(max_idx[1]).getObjective(1) - population_.get(max_idx[0]).getObjective(1));
//		double temp2 = Math.pow(population_.get(max_idx[1]).getObjective(0) - population_.get(max_idx[0]).getObjective(0), 2.0) + 
//				Math.pow(population_.get(max_idx[1]).getObjective(1) - population_.get(max_idx[0]).getObjective(1), 2.0);
//		double constant = Math.sqrt(temp2);
//		double tempDist = Math.abs(temp1) / constant;
//		maxIdx  = 0;
//		maxDist = tempDist;
//		for (int i = 1; i < populationSize_; i++) {
//			temp1 = (population_.get(max_idx[1]).getObjective(0) - population_.get(max_idx[0]).getObjective(0)) *
//					(population_.get(max_idx[0]).getObjective(1) - population_.get(i).getObjective(1)) - 
//					(population_.get(max_idx[0]).getObjective(0) - population_.get(i).getObjective(0)) * 
//					(population_.get(max_idx[1]).getObjective(1) - population_.get(max_idx[0]).getObjective(1));
//			tempDist = Math.abs(temp1) / constant;
//			if (tempDist > maxDist) {
//				maxIdx  = i;
//				maxDist = tempDist;
//			}
//		}
//		
//		return population_.get(maxIdx);
//	}
} // NSGA-II
