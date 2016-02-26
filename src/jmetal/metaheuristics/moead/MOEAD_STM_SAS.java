//  MOEAD_STM.java
//
//  Author:
//       Ke Li <keli.genius@gmail.com>
//       <http://www.cs.bham.ac.uk/~likw/>
//  
//	Reference:
//  	 K. Li, Q. Zhang, S. Kwong, M. Li and R. Wang, "Stable Matching-Based Selection in Evolutionary Multiobjective Optimization",
//		 IEEE Transactions on Evolutionary Computation, 18(6): 909-923, 2014. DOI: 10.1109/TEVC.2013.2293776
//
//  Copyright (c) 2016 Ke Li
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

package jmetal.metaheuristics.moead;

import jmetal.core.*;
import jmetal.util.JMException;
import jmetal.util.PseudoRandom;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;

public class MOEAD_STM_SAS extends Algorithm {

	private int populationSize_;

	// population repository
	private SolutionSet population_;
	private SolutionSet currentOffspring_;
	private SolutionSet union_;

	// stores the values of the individuals
	private Solution[] savedValues_;

	// subproblem utility
	private double[] utility_;

	// ideal point
	double[] z_;

	// nadir point
	double[] nz_;

	// weight vectors
	double[][] lambda_;

	// neighborhood size
	int T_;

	// neighborhood structure
	int[][] neighborhood_;

	// probability that parent solutions are selected from neighborhood
	double delta_;

	String functionType_;
	int evaluations_;

	Operator crossover_;
	Operator mutation_;

	String dataDirectory_;

  	/**
  	 * Constructor
  	 * @param problem Problem to solve
  	 */
	public MOEAD_STM_SAS(Problem problem) {
		super(problem);

		functionType_ = "TCH";
	}

	public SolutionSet execute() throws JMException, ClassNotFoundException {
		
		int type;
		int maxEvaluations;
		
		// knee point which might be used as the output
		Solution kneeIndividual = new Solution(problem_);

		evaluations_    = 0;
		dataDirectory_  = this.getInputParameter("dataDirectory").toString();
		maxEvaluations  = ((Integer) this.getInputParameter("maxEvaluations")).intValue();
		populationSize_ = ((Integer) this.getInputParameter("populationSize")).intValue();

		population_  = new SolutionSet(populationSize_);
		savedValues_ = new Solution[populationSize_];
		utility_     = new double[populationSize_];
		for (int i = 0; i < utility_.length; i++) {
			utility_[i] = 1.0;
		}

		T_ 	   = 20;
		delta_ = 0.9;

		z_ 			  = new double[problem_.getNumberOfObjectives()];
	    nz_ 		  = new double[problem_.getNumberOfObjectives()];
	    lambda_ 	  = new double[populationSize_][problem_.getNumberOfObjectives()];
	    neighborhood_ = new int[populationSize_][T_];

		crossover_ = operators_.get("crossover");
		mutation_  = operators_.get("mutation");

		/* STEP 1. INITIALIZATION */
		// STEP 1.1. compute Euclidean distances between weight vectors and find T
		initUniformWeight();
		initNeighborhood();

		// STEP 1.2. initialize population
		initPopulation();

		// STEP 1.3. initialize the ideal and nadir points
		initIdealPoint();
		initNadirPoint();

		int iteration = 0;
		/* STEP 2. UPDATE */
		do {
			// select the satisfied subproblems
			List<Integer> order = tour_selection(10);
			currentOffspring_   = new SolutionSet(order.size());
			
			for (int i = 0; i < order.size(); i++) {
				int n = order.get(i);

				double rnd = PseudoRandom.randDouble();

				// STEP 2.1. mating selection based on probability
				if (rnd < delta_)
				{
					type = 1; // neighborhood
				} else {
					type = 2; // whole population
				}
				Solution child;
				Solution[] parents = new Solution[3];
				Vector<Integer> p = new Vector<Integer>();
				
				parents = matingSelection(p, n, 2, type);

				// apply DE crossover and polynomial mutation
				child = (Solution) crossover_.execute(new Object[] {population_.get(n), parents});
				mutation_.execute(child);

				// evaluation
				problem_.evaluate(child);
				evaluations_++;

				// STEP 2.3. update the ideal and nadir points
				updateReference(child);
				updateNadirPoint(child);

				// add into the offspring population
				currentOffspring_.add(child);
			} // for
			
			// Combine the parent and the current offspring populations
			union_ = ((SolutionSet) population_).union(currentOffspring_);

			// selection process
			selection();

			// update the utility value of subproblems
			iteration++;
			if (iteration % 30 == 0) {
				comp_utility();
			}
		} while (evaluations_ <= maxEvaluations);
		
		// find the knee point
		kneeIndividual = kneeSelection();
		
		return population_;
	}
	
	/**
  	 * Select the next parent population, based on the stable matching criteria
  	 */
	public void selection() {

		int[] idx = new int[populationSize_];
		double[] nicheCount = new double[populationSize_];

		int[][]    solPref   = new int[union_.size()][];
		double[][] solMatrix = new double[union_.size()][];
		double[][] distMatrix    = new double[union_.size()][];
		double[][] fitnessMatrix = new double[union_.size()][];

		for (int i = 0; i < union_.size(); i++) {
			solPref[i]   = new int[populationSize_];
			solMatrix[i] = new double[populationSize_];
			distMatrix[i]    = new double[populationSize_];
			fitnessMatrix[i] = new double[populationSize_];
		}
		int[][]    subpPref   = new int[populationSize_][];
		double[][] subpMatrix = new double[populationSize_][];
		for (int i = 0; i < populationSize_; i++) {
			subpPref[i]   = new int[union_.size()];
			subpMatrix[i] = new double[union_.size()];
		}

		// Calculate the preference values of solution matrix
		for (int i = 0; i < union_.size(); i++) {
			int minIndex = 0;
			for (int j = 0; j < populationSize_; j++) {
				fitnessMatrix[i][j] = fitnessFunction(union_.get(i), lambda_[j]);
				distMatrix[i][j]  	= calculateDistance2(union_.get(i), lambda_[j]);
				if (distMatrix[i][j] < distMatrix[i][minIndex])
					minIndex = j;
			}
			nicheCount[minIndex] = nicheCount[minIndex] + 1;
		}

		// calculate the preference values of subproblem matrix and solution matrix
		for (int i = 0; i < union_.size(); i++) {
			for (int j = 0; j < populationSize_; j++) {
				subpMatrix[j][i] = fitnessFunction(union_.get(i), lambda_[j]);
				solMatrix[i][j] = distMatrix[i][j] + nicheCount[j];
			}
		}

		// sort the preference value matrix to get the preference rank matrix
		for (int i = 0; i < populationSize_; i++) {
			for (int j = 0; j < union_.size(); j++)
				subpPref[i][j] = j;
			Utils.QuickSort(subpMatrix[i], subpPref[i], 0, union_.size() - 1);
		}
		for (int i = 0; i < union_.size(); i++) {
			for (int j = 0; j < populationSize_; j++)
				solPref[i][j] = j;
			Utils.QuickSort(solMatrix[i], solPref[i], 0, populationSize_ - 1);
		}

		idx = stableMatching(subpPref, solPref, populationSize_, union_.size());

		for (int i = 0; i < populationSize_; i++)
			population_.replace(i, new Solution(union_.get(idx[i])));
	}
  
  	/**
	 * Return the stable matching between 'subproblems' and 'solutions'
	 * ('subproblems' propose first). It is worth noting that the number of
	 * solutions is larger than that of the subproblems.
	 * 
	 * @param manPref
	 * @param womanPref
	 * @param menSize
	 * @param womenSize
	 * @return
	 */
	public int[] stableMatching(int[][] manPref, int[][] womanPref, int menSize, int womenSize) {
		
		// Indicates the mating status
		int[] statusMan   = new int[menSize];
		int[] statusWoman = new int[womenSize];

		final int NOT_ENGAGED = -1;
		for (int i = 0; i < womenSize; i++)
			statusWoman[i] = NOT_ENGAGED;

		// List of men that are not currently engaged.
		LinkedList<Integer> freeMen = new LinkedList<Integer>();
		for (int i = 0; i < menSize; i++)
			freeMen.add(i);

		// next[i] is the next woman to whom i has not yet proposed.
		int[] next = new int[womenSize];

		while (!freeMen.isEmpty()) {
			int m = freeMen.remove();
			int w = manPref[m][next[m]];
			next[m]++;
			if (statusWoman[w] == NOT_ENGAGED) {
				statusMan[m]   = w;
				statusWoman[w] = m;
			} else {
				int m1 = statusWoman[w];
				if (prefers(m, m1, womanPref[w], menSize)) {
					statusMan[m]   = w;
					statusWoman[w] = m;
					freeMen.add(m1);
				} else {
					freeMen.add(m);
				}
			}
		}
		
		return statusMan;
	}
	
  	/**
  	 * Returns true in case that a given woman prefers x to y.
  	 * @param x
  	 * @param y
  	 * @param womanPref
  	 * @return
  	 */
	public boolean prefers(int x, int y, int[] womanPref, int size) {
		
		for (int i = 0; i < size; i++) {
			int pref = womanPref[i];
			if (pref == x)
				return true;
			if (pref == y)
				return false;
		}
		// this should never happen.
		System.out.println("Error in womanPref list!");
		return false;
	}
	
	/**
	 * Calculate the perpendicular distance between the solution and reference
	 * line
	 * 
	 * @param individual
	 * @param lambda
	 * @return
	 */
	public double calculateDistance(Solution individual, double[] lambda) {
		double scale;
		double distance;

		double[] vecInd  = new double[problem_.getNumberOfObjectives()];
		double[] vecProj = new double[problem_.getNumberOfObjectives()];
		
		// vecInd has been normalized to the range [0,1]
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			vecInd[i] = (individual.getObjective(i) - z_[i]) / (nz_[i] - z_[i]);

		scale = innerproduct(vecInd, lambda) / innerproduct(lambda, lambda);
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			vecProj[i] = vecInd[i] - scale * lambda[i];

		distance = norm_vector(vecProj);
		
		return distance;
	}
	
	/**
	 * Calculate the perpendicular distance between the solution and reference line
	 * @param individual
	 * @param lambda
	 * @return
	 */
	public double calculateDistance2(Solution individual, double[] lambda) {

		double distance;
		double distanceSum = 0.0;

		double[] vecInd  	   = new double[problem_.getNumberOfObjectives()];
		double[] normalizedObj = new double[problem_.getNumberOfObjectives()];

		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			distanceSum += individual.getObjective(i);
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			normalizedObj[i] = individual.getObjective(i) / distanceSum;
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			vecInd[i] = normalizedObj[i] - lambda[i];

		distance = norm_vector(vecInd);

		return distance;
	}
	
	/**
	 * Initialize the weight vectors for subproblems (We only use the data that are already available)
	 */
	public void initUniformWeight() {
		String dataFileName;
//		dataFileName = "W" + problem_.getNumberOfObjectives() + "D_"
//				+ populationSize_ + ".dat";

		dataFileName = "pW" + problem_.getNumberOfObjectives() + "D_"
				+ problem_.getName() + "_IN_3.dat";

		try {
			// Open the file
			FileInputStream fis = new FileInputStream(dataDirectory_ + "/"
					+ dataFileName);
			InputStreamReader isr = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(isr);

			int i = 0;
			int j = 0;
			String aux = br.readLine();
			while (aux != null) {
				StringTokenizer st = new StringTokenizer(aux);
				j = 0;
				while (st.hasMoreTokens()) {
					double value = (new Double(st.nextToken())).doubleValue();
					lambda_[i][j] = value;
					j++;
				}
				aux = br.readLine();
				i++;
			}
			br.close();
		} catch (Exception e) {
			System.out
					.println("initUniformWeight: failed when reading for file: "
							+ dataDirectory_ + "/" + dataFileName);
			e.printStackTrace();
		}
	} // initUniformWeight

	/**
	 * Compute the utility of subproblems
	 */
	public void comp_utility() {
		
		double f1, f2, uti, delta;
		
		for (int i = 0; i < populationSize_; i++) {
			f1    = fitnessFunction(population_.get(i), lambda_[i]);
			f2 	  = fitnessFunction(savedValues_[i], lambda_[i]);
			
			delta = f2 - f1;
			if (delta > 0.001)
				utility_[i] = 1.0;
			else {
				uti 		= (0.95 + (0.05 * delta / 0.001)) * utility_[i];
				utility_[i] = uti < 1.0 ? uti : 1.0;
			}
			savedValues_[i] = new Solution(population_.get(i));
		}
	}

	/**
	 * Initialize the neighborhood of subproblems
	 */
	public void initNeighborhood() {
		double[] x = new double[populationSize_];
		int[] idx = new int[populationSize_];

		for (int i = 0; i < populationSize_; i++) {
			// calculate the distances based on weight vectors
			for (int j = 0; j < populationSize_; j++) {
				x[j] = Utils.distVector(lambda_[i], lambda_[j]);
				idx[j] = j;
			}

			// find 'niche' nearest neighboring subproblems
			Utils.minFastSort(x, idx, populationSize_, T_);

			for (int k = 0; k < T_; k++) {
				neighborhood_[i][k] = idx[k];
			}
		}
	}

  /**
   * Initialize the population
   * @throws JMException
   * @throws ClassNotFoundException
   */
  public void initPopulation() throws JMException, ClassNotFoundException {
	  for (int i = 0; i < populationSize_; i++) {
      Solution newSolution = new Solution(problem_);

      problem_.evaluate(newSolution);
      evaluations_++;
      population_.add(newSolution) ;
      savedValues_[i] = new Solution(newSolution);
    }
  }

	public void initPopulation_Read() throws JMException, ClassNotFoundException {
		String popFileName = "FUN1.dat";

		try {
			// Open the file
			FileInputStream fis = new FileInputStream(dataDirectory_ + "/"
					+ popFileName);
			InputStreamReader isr = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(isr);

			int i = 0;
			int j = 0;
			String aux = br.readLine();
			while (aux != null) {
				StringTokenizer st = new StringTokenizer(aux);
				j = 0;
				Solution newSolution = new Solution(problem_);
				while (st.hasMoreTokens()) {
					double value = (new Double(st.nextToken())).doubleValue();
					newSolution.setObjective(j, value);
					j++;
				}
				population_.add(newSolution) ;
				savedValues_[i] = new Solution(newSolution);
				aux = br.readLine();
				i++;
			}
			br.close();
		} catch (Exception e) {
			System.out
					.println("initUniformWeight: failed when reading for file: "
							+ dataDirectory_ + "/" + popFileName);
			e.printStackTrace();
		}
	}

  	/**
  	 * Initialize the ideal point
	 * @throws JMException
	 * @throws ClassNotFoundException
	 */
	void initIdealPoint() throws JMException, ClassNotFoundException {
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
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			nz_[i] = -1.0e+30;

		for (int i = 0; i < populationSize_; i++)
			updateNadirPoint(population_.get(i));
	}

	/**
  	 * Mating selection is used to select the mating parents for offspring generation
  	 * @param list : the set of the indexes of selected mating parents
  	 * @param cid  : the id of current subproblem
  	 * @param size : the number of selected mating parents
  	 * @param type : 1 - neighborhood; otherwise - whole population
  	 */
	public Solution[] matingSelection(Vector<Integer> list, int cid, int size, int type) {
		
		int ss, r, p;
		
		Solution[] parents = new Solution[3];
		
		ss = neighborhood_[cid].length;
		while (list.size() < size) {
			if (type == 1) {
				r = PseudoRandom.randInt(0, ss - 1);
				p = neighborhood_[cid][r];
			} else {
				p = PseudoRandom.randInt(0, populationSize_ - 1);
			}
			boolean flag = true;
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) == p) // p is in the list
				{
					flag = false;
					break;
				}
			}

			if (flag) {
				list.addElement(p);
			}
		}
		parents[0] = population_.get(list.get(0));
		parents[1] = population_.get(list.get(1));
		parents[2] = population_.get(cid);
		
		return parents;
	} // matingSelection

	public List<Integer> tour_selection(int depth) {

		int i2, s2;
		int threshold;
		
		// selection based on utility
		List<Integer> selected  = new ArrayList<Integer>();
		List<Integer> candidate = new ArrayList<Integer>();

		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			selected.add(i);

		// set of unselected weights
		for (int i = problem_.getNumberOfObjectives(); i < populationSize_; i++)
			candidate.add(i);

		threshold = (int) (populationSize_ / 5);
		while (selected.size() < threshold) {
			int best_idd = (int) (PseudoRandom.randDouble() * candidate.size());
			int best_sub = candidate.get(best_idd);
			
			for (int i = 1; i < depth; i++) {
				i2 = (int) (PseudoRandom.randDouble() * candidate.size());
				s2 = candidate.get(i2);
				if (utility_[s2] > utility_[best_sub]) {
					best_idd = i2;
					best_sub = s2;
				}
			}
			selected.add(best_sub);
			candidate.remove(best_idd);
		}
		return selected;
	}

   	/**
   	 * Update the ideal point, it is just an approximation with the best value for each objective
   	 * @param individual
   	 */
	void updateReference(Solution individual) {
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
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
			if (individual.getObjective(i) > nz_[i])
				nz_[i] = individual.getObjective(i);
		}
	}
	
	/**
	 * Calculate the dot product of two vectors
	 * @param vec1
	 * @param vec2
	 * @return
	 */
	public double innerproduct(double[] vec1, double[] vec2) {
		double sum = 0;
		
		for (int i = 0; i < vec1.length; i++)
			sum += vec1[i] * vec2[i];
		
		return sum;
	}

	/**
	 * Calculate the norm of the vector
	 * @param z
	 * @return
	 */
	public double norm_vector(double[] z) {
		double sum = 0;
		
		for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
			sum += z[i] * z[i];
		
		return Math.sqrt(sum);
	}

	/**
	 * Calculate the fitness value of a given individual, based on the specific scalarizing function
	 * @param individual
	 * @param lambda
	 * @return
	 */
	double fitnessFunction(Solution individual, double[] lambda) {
		double fitness;
		fitness = 0.0;

		if (functionType_.equals("TCH")) {
			double maxFun = -1.0e+30;

			for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
				double diff = Math.abs(individual.getObjective(i) - z_[i]);

				double feval;
				if (lambda[i] == 0) {
					feval = diff / 0.000001;
				} else {
					feval = diff / lambda[i];
				}
				if (feval > maxFun) {
					maxFun = feval;
				}
			}
			fitness = maxFun;
		} else if (functionType_.equals("Norm_TCH"))  {
			double maxFun = -1.0e+30;
			double[] normalized_obj = new double[problem_.getNumberOfObjectives()];
			
			// normalization
			for (int i = 0; i < problem_.getNumberOfObjectives(); i++) 
				normalized_obj[i] = (individual.getObjective(i) - z_[i]) / (nz_[i] - z_[i]);
			
			for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
				double diff = Math.abs(individual.getObjective(i));
				
				double feval;
				if (lambda[i] == 0)
					feval = diff / 0.000001;
				else
					feval = diff / lambda[i];
				if (feval > maxFun)
					maxFun = feval;
			}
			fitness = maxFun;
		} else if (functionType_.equals("Norm_ITCH")) {
			double max_fun = -1.0e+30;
			double[] normalized_obj = new double[problem_.getNumberOfObjectives()];
			
			// normalization
			for (int i = 0; i < problem_.getNumberOfObjectives(); i++) 
				normalized_obj[i] = (individual.getObjective(i) - z_[i]) / (nz_[i] - z_[i]);
			
			for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
				double diff = Math.abs(individual.getObjective(i));
				double feval;
				if (lambda[i] == 0)
					feval = 0.000001 * diff;
				else
					feval = diff * lambda[i];
				if (feval > max_fun)
					max_fun = feval;
			}
			
			fitness = max_fun;
		} else if (functionType_.equals("ITCH")) {			
			double max_fun = -1.0e+30;
			
			for (int n = 0; n < problem_.getNumberOfObjectives(); n++) {
				double diff = (individual.getObjective(n) - z_[n]);
				double feval;
				if (lambda[n] == 0)
					feval = 0.0001 * diff;
				else
					feval = diff * lambda[n];
				if (feval > max_fun)
					max_fun = feval;
			}
			fitness = max_fun;
		} else if (functionType_.equals("PBI"))
		{
			double theta; // penalty parameter
			theta = 5.0;

			// normalize the weight vector (line segment)
			double nd = norm_vector(lambda);
			for (int i = 0; i < problem_.getNumberOfObjectives(); i++)
				lambda[i] = lambda[i] / nd;

			double[] realA = new double[problem_.getNumberOfObjectives()];
			double[] realB = new double[problem_.getNumberOfObjectives()];

			// difference beween current point and reference point
			for (int n = 0; n < problem_.getNumberOfObjectives(); n++)
				realA[n] = (individual.getObjective(n) - z_[n]);

			// distance along the line segment
			double d1 = Math.abs(innerproduct(realA, lambda));

			// distance to the line segment
			for (int n = 0; n < problem_.getNumberOfObjectives(); n++)
				realB[n] = (individual.getObjective(n) - (z_[n] + d1
						* lambda[n]));
			double d2 = norm_vector(realB);

			fitness = d1 + theta * d2;
		} else {
			System.out.println("MOEAD.fitnessFunction: unknown type "
					+ functionType_);
			System.exit(-1);
		}
		return fitness;
	}
	
	/**
	 * This is used to find the knee point from a set of solutions
	 * 
	 * @param population
	 * @return
	 */
	Solution kneeSelection() {
		
		int f1_index  = 0;
		int f2_index  = 0;
		double f1_max = -Double.MAX_VALUE;
		double f2_max = -Double.MIN_VALUE;
		
		// finding the extreme solution for f1
		for (int i = 0; i < populationSize_; i++) {
			for (int j = 0; j < problem_.getNumberOfObjectives(); j++) {
				// search for the extreme solution for f1
				if (population_.get(i).getObjective(j) > f1_max) {
					f1_index = i;
					f1_max   = population_.get(i).getObjective(j);
				}
				// search for the extreme solution for f2
				if (population_.get(i).getObjective(j) > f2_max) {
					f2_index = i;
					f2_max   = population_.get(i).getObjective(j);
				}
			}
		}
		
		if (f1_index == f2_index)
			System.out.println("Watch out! Two equal extreme solutions cannot happen!");
		
		int maxIdx;
		double maxDist;
		double temp1 = (population_.get(f2_index).getObjective(1) - population_.get(f1_index).getObjective(1)) * 
				(population_.get(f1_index).getObjective(2) - population_.get(0).getObjective(2)) * 
				(population_.get(f1_index).getObjective(1) - population_.get(0).getObjective(1)) * 
				(population_.get(f2_index).getObjective(2) - population_.get(f1_index).getObjective(2));
		double temp2 = Math.pow(population_.get(f2_index).getObjective(1) - population_.get(f1_index).getObjective(1), 2.0) + 
				Math.pow(population_.get(f2_index).getObjective(2) - population_.get(f1_index).getObjective(2), 2.0);
		double constant = Math.sqrt(temp2);
		double tempDist = Math.abs(temp1) / constant;
		maxIdx  = 0;
		maxDist = tempDist;
		for (int i = 1; i < populationSize_; i++) {
			temp1 = (population_.get(f2_index).getObjective(1) - population_.get(f1_index).getObjective(1)) * 
					(population_.get(f1_index).getObjective(2) - population_.get(i).getObjective(2)) * 
					(population_.get(f1_index).getObjective(1) - population_.get(i).getObjective(1)) * 
					(population_.get(f2_index).getObjective(2) - population_.get(f1_index).getObjective(2));
			tempDist = Math.abs(temp1) / constant;
			if (tempDist > maxDist) {
				maxIdx  = i;
				maxDist = tempDist;
			}
		}
		
		return population_.get(maxIdx);
	}

} // moea/D-STM