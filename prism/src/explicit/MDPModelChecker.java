//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package explicit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import acceptance.AcceptanceOmega;
import acceptance.AcceptanceReach;
import acceptance.AcceptanceType;
import automata.DA;
import common.IterableBitSet;
import explicit.LTLModelChecker.LTLProduct;
import explicit.rewards.MCRewards;
import explicit.rewards.MCRewardsFromMDPRewards;
import explicit.rewards.MDPRewards;
import explicit.rewards.MDPRewardsSimple;
import explicit.rewards.Rewards;
import parser.VarList;
import parser.ast.Declaration;
import parser.ast.DeclarationIntUnbounded;
import parser.ast.Expression;
import parser.ast.ExpressionFunc;
import parser.ast.ExpressionReward;
import parser.ast.RewardStruct;
import parser.type.TypeInt;

import prism.Prism;
import prism.PrismComponent;
import prism.PrismDevNullLog;
import prism.PrismException;
import prism.PrismFileLog;
import prism.PrismLog;
import prism.PrismUtils;
import strat.MDStrategy;
import strat.MDStrategyArray;

import ws_test.wlabel;
import java.awt.Point;

/**
 * Explicit-state model checker for Markov decision processes (MDPs).
 */
public class MDPModelChecker extends ProbModelChecker {
	/**
	 * Create a new MDPModelChecker, inherit basic state from parent (unless
	 * null).
	 */
	public MDPModelChecker(PrismComponent parent) throws PrismException {
		super(parent);
	}

	// Model checking functions

	@Override
	protected StateValues checkExpressionFunc(Model model, ExpressionFunc expr, BitSet statesOfInterest)
			throws PrismException {
		switch (expr.getNameCode()) {
		case ExpressionFunc.PARTIAL:{
			//genStrat = true;
			// return checkPartialSat(model, expr, statesOfInterest);
			// return checkPartialSatMod(model, expr, statesOfInterest);
			 
			// return checkWS(model, expr, statesOfInterest);
			//return doNothing(model, expr, statesOfInterest);
			return trySkipping(model,expr,statesOfInterest);
			 }
		default:
			return super.checkExpressionFunc(model, expr, statesOfInterest);
		}
	}

	protected StateValues doNothing(Model model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException {
		StateValues test = null;
		LTLModelChecker mcLtl;
		StateValues probsProduct, probs, costsProduct, costs, rewsProduct, rews;
		MDPModelChecker mcProduct;
		LTLModelChecker.LTLProduct<MDP> product, kutta, reuse;
		MDP productMdp, kuttaykabacha;
		DA<BitSet, ? extends AcceptanceOmega> da;
		DA<BitSet, ? extends AcceptanceOmega> wda;
		Vector<BitSet> labelBS;
		String saveplace = "/home/fatma/hubic/phD/work/code/mdpltl/prism-svn/prism/tests/fatma_tests/dotfiles/";

		// For LTL model checking routines
		mcLtl = new LTLModelChecker(this);

		ExpressionReward exprRew = (ExpressionReward) expr.getOperand(0);
		Expression ltl = exprRew.getExpression();
		RewardStruct costStruct = exprRew.getRewardStructByIndexObject(modulesFile, modulesFile.getConstantValues());
		int r = exprRew.getRewardStructIndexByIndexObject(modelInfo, constantValues);
		Rewards costsModel = constructRewards(model, costStruct);

		// build DFA
		AcceptanceType[] allowedAcceptance = { AcceptanceType.RABIN, AcceptanceType.REACH };
		labelBS = new Vector<BitSet>();
		da = mcLtl.constructDAForLTLFormula(this, model, ltl, labelBS, allowedAcceptance);

		mainLog.println("printing model ");
		model.exportToDotFile(saveplace + "mdp.dot");
		mainLog.println("printing da ");
		// File out1 = new File(saveplace+"da.dot");
		PrintStream dadot;
		try {
			dadot = new PrintStream(new File(saveplace + "da.dot"));
			da.printDot(dadot);
			dadot.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (!(da.getAcceptance() instanceof AcceptanceReach)) {
			mainLog.println("\nAutomaton is not a DFA. Breaking.");
			// Dummy return vector
			return new StateValues(TypeInt.getInstance(), model);
		}
		// trying out the transition stuff *******************
		wda = mcLtl.constructDAForLTLFormula(this, model, ltl, labelBS, allowedAcceptance);
		List<String> aplist = new ArrayList<String>(da.getAPList());
		List<String> aplistc = new ArrayList<String>(aplist);
		// add the empty set
		aplist.add(" ");
		// assuming we don't care about sets with more than one element so if
		// numaplist = 2 we dont care about {0,1} if numaplist = 3 {0,1} {1,2}
		// {0,1,2}
		// making a table thing
		int num_ap = aplist.size();
		for (int i = 0; i < num_ap; i++) {
			for (int j = 0; j < num_ap; j++) {
				if (i != j) {
					aplistc.add(aplist.get(i) + aplist.get(j));
				}
			}
		}
		// blind wda
		BitSet lab;
		for (int i = 0; i < da.size(); i++) {
			for (int j = 0; j < da.size(); j++) {
				int numedges = da.getNumEdges(i, j);
				List<BitSet> edgelabels = da.getEdgeLabels(i, j);
				wlabel tmp = new wlabel(num_ap - 1);
				StringBuilder sb = new StringBuilder();
				for (BitSet b : edgelabels) {
					// for each bitset make a new one

					tmp.print(mainLog, b);
					for (int k = 0; k < tmp.phy.size(); k++) {
						BitSet tlab = tmp.phy.get(k);
						if (!tlab.equals(b))
							wda.addEdge(i, tlab, j);
					}
					sb.append(b.toString());
					sb.append("\t");
				}

				mainLog.println(numedges + " (" + i + "," + j + ") " + sb.toString());
				// for(int e = 0; e<numedges; e++)
				// {
				// lab = da.get
				//
				// byte arr[] = lab.toByteArray();
				// // System.arraycopy(lab.toByteArray(), 0, arr, 0,
				// lab.toByteArray().length);
				// if (arr.length > 0)
				// mainLog.println(lab.size()+"(s,e,s')"+i+","+e+","+j+lab.toString()+"-"+arr[0]+"
				// edges"+numedges);
				// else
				// mainLog.println(lab.size()+"(s,e,s')"+i+","+e+","+j+lab.toString()+"
				// edges"+numedges);
				//
				//
				// }
			}
		}

		try {
			dadot = new PrintStream(new File(saveplace + "wda.dot"));
			wda.printDot(dadot);
			dadot.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// build product
		int numStates = model.getNumStates();
		BitSet bsInit = new BitSet(numStates);
		for (int i = 0; i < numStates; i++) {
			bsInit.set(i, model.isInitialState(i));
		}
		// create a list of da states and mdp states
		List<Point> all_s = new Vector<Point>();

		MDPSimple mdpProd = new MDPSimple();
		for (int q_i = 0; q_i < numStates; q_i++) {
			for (int z_i = 0; z_i < da.size(); z_i++) {
				all_s.add(new Point(q_i, z_i));
				mainLog.println("adding (q,z) (" + q_i + "," + z_i + ")");
			}
		}

		// add states to product
		mdpProd.addStates(all_s.size());
		mainLog.println("number of states added = " + all_s.size() + " prodmdp states = " + mdpProd.numStates);
		int numAPs = da.getAPList().size();
		BitSet qp_labels = new BitSet(numAPs);
		BitSet q_labels = new BitSet(numAPs);

		MDPSimple mdpProd_actionCopies = new MDPSimple(mdpProd);
		// for each AP there is a cost for skipping it
		// the powerset is 2^numAPs
		int ps = (int) Math.pow(2.0, (double) numAPs);
		double apCosts[] = new double[ps + 1]; // the first one will be 0 for
												// nothing
		// lets just set them all ourselves
		apCosts[0] = 0;
		for (int ap = 1; ap < ps + 1; ap++) {
			apCosts[ap] = ap + 10; // this is just so i can test stuff
		}
		// if only I cared about much else
		// mitti pao :P
		// create a thing for MDPRewards, add the states or whatever and for
		// each transition add a cost.
		// also we need to be making copies ish ... or do we ... cuz lots of
		// things will have their own stuff
		// look into this
		MDPRewardsSimple skipcosts = new MDPRewardsSimple(mdpProd.numStates);

		for (int i = 0; i < mdpProd.numStates; i++) {
			for (int j = i; j < mdpProd.numStates; j++) {
				int q = all_s.get(i).x;
				int z = all_s.get(i).y;
				int qp = all_s.get(j).x;
				int zp = all_s.get(j).y;

				boolean mdphasedge = model.isSuccessor(q, qp);

				List<BitSet> edges_da = da.getEdgeLabels(z, zp);
				int edges_da_num = edges_da.size();

				for (int k = 0; k < numAPs; k++) {
					qp_labels.set(k, labelBS.get(Integer.parseInt(da.getAPList().get(k).substring(1))).get(qp));
					q_labels.set(k, labelBS.get(Integer.parseInt(da.getAPList().get(k).substring(1))).get(q));
				}

				// finding the actions that get us from q to qp
				int numchoices = ((MDP) model).getNumChoices(q);
				int numchoicesqp = ((MDP) model).getNumChoices(qp);
				// for each choice get the state and action label
				// does this choice have this as a successor state
				List<Point> qtoqp = new ArrayList<Point>();
				List<Point> qptoq = new ArrayList<Point>();

				for (int c = 0; c < numchoices; c++) {
					Iterator<Map.Entry<Integer, Double>> iter = ((MDP) model).getTransitionsIterator(q, c);
					while (iter.hasNext()) {
						Map.Entry<Integer, Double> e = iter.next();
						if (qp == e.getKey()) {
							Point p = new Point();
							p.setLocation((double) c, e.getValue());

							qtoqp.add(p);
						}

					}
				}

				for (int c = 0; c < numchoicesqp; c++) {
					Iterator<Map.Entry<Integer, Double>> iter = ((MDP) model).getTransitionsIterator(qp, c);
					while (iter.hasNext()) {
						Map.Entry<Integer, Double> e = iter.next();
						if (q == e.getKey()) {
							Point p = new Point();
							p.setLocation((double) c, e.getValue());

							qptoq.add(p);
						}
					}
				}

				String info = "(d,d') e ->D (" + q + "," + qp + ") " + mdphasedge + " L(d')= " + qp_labels.toString()
						+ " (z,z') " + "(" + z + ", " + zp + ") z'=z " + (zp == z) + " t -> z' = d(z,t) = ";
				StringBuilder sb2 = new StringBuilder();
				for (int k = 0; k < edges_da.size(); k++) {

					sb2.append(edges_da.get(k).toString());
					sb2.append("\t");
				}
				info = info + sb2.toString();
				// mainLog.println(info);
				if (mdphasedge & edges_da_num > 0) {
					StringBuilder edsb = new StringBuilder();

					for (int ed = 0; ed < edges_da_num; ed++) {
						// for each action I have I can add an edge
						for (int c = 0; c < qtoqp.size(); c++) {
							Distribution probd = new Distribution();
							probd.add(j, qtoqp.get(c).getY());
							mdpProd.addActionLabelledChoice(i, probd,
									((MDP) model).getAction(q, (int) qtoqp.get(c).getX()));
							mdpProd_actionCopies.addActionLabelledChoice(i, probd,
									((MDP) model).getAction(q, (int) qtoqp.get(c).getX()).toString() + c + ed);
							// get the cost
							double cost = 0;
							boolean max_semantics = true;
							if (!qp_labels.equals(edges_da.get(ed))) {
								int ap1 = 0;
								long arr[] = qp_labels.toLongArray();
								if (arr.length > 0) {
									ap1 = (int) arr[0] + 1;
								}

								int ap2 = 0;
								long arr2[] = edges_da.get(ed).toLongArray();
								if (arr2.length > 0)
									ap2 = (int) arr2[0] + 1;
								if (max_semantics) {

									if (apCosts[ap1] < apCosts[ap2]) {
										cost = apCosts[ap2];
									} else
										cost = apCosts[ap1];
								}

								else {
									// sum
									cost = apCosts[ap1] + apCosts[ap2];
								}
							}
							skipcosts.addToTransitionReward(i, mdpProd_actionCopies.getNumChoices(i), cost);
							mainLog.println(
									"cost: " + qp_labels.toString() + "," + edges_da.get(ed).toString() + ":" + cost);

						}
						edsb.append(edges_da.get(ed).toString());
					}

					// add costs for this to mdp todo

					mainLog.println("(" + q + "," + z + ")--" + qp_labels.toString() + "," + edsb.toString() + " --> ("
							+ qp + "," + zp + ")");

				}
				if (i != j) {
					boolean mdphasedge_rev = model.isSuccessor(qp, q);
					List<BitSet> edges_da_rev = da.getEdgeLabels(zp, z);
					int edges_da_rev_num = edges_da_rev.size();

					if (mdphasedge_rev & edges_da_rev_num > 0) {
						StringBuilder edrsb = new StringBuilder();

						for (int ed = 0; ed < edges_da_rev_num; ed++) {
							edrsb.append(edges_da_rev.get(ed).toString());
							for (int c = 0; c < qptoq.size(); c++) {
								Distribution probd = new Distribution();
								probd.add(i, qptoq.get(c).getY());
								mdpProd.addActionLabelledChoice(j, probd,
										((MDP) model).getAction(qp, (int) qptoq.get(c).getX()));

								mdpProd_actionCopies.addActionLabelledChoice(j, probd,
										((MDP) model).getAction(qp, (int) qptoq.get(c).getX()).toString() + c + ed);
								// get the cost
								double cost = 0;
								boolean max_semantics = true;
								if (!q_labels.equals(edges_da_rev.get(ed))) {
									int ap1 = 0;
									long arr[] = q_labels.toLongArray();
									if (arr.length > 0) {
										ap1 = (int) arr[0] + 1;
									}

									int ap2 = 0;
									long arr2[] = edges_da_rev.get(ed).toLongArray();
									if (arr2.length > 0)
										ap2 = (int) arr2[0] + 1;
									if (max_semantics) {

										if (apCosts[ap1] < apCosts[ap2]) {
											cost = apCosts[ap2];
										} else
											cost = apCosts[ap1];
									}

									else {
										// sum
										cost = apCosts[ap1] + apCosts[ap2];
									}
								}
								skipcosts.addToTransitionReward(j, mdpProd_actionCopies.getNumChoices(j), cost);
								mainLog.println("cost: " + q_labels.toString() + "," + edges_da_rev.get(ed).toString()
										+ ":" + cost);
							}
						}
						mainLog.println("(" + qp + "," + zp + ")--" + q_labels.toString() + "," + edrsb.toString()
								+ " --> (" + q + "," + z + ")");
					}
					String info2 = "(d,d') e ->D (" + qp + "," + q + ") " + mdphasedge_rev + " L(d')= "
							+ q_labels.toString() + " (z,z') " + "(" + zp + ", " + z + ") z'=z " + (zp == z)
							+ " t -> z' = d(z,t) = ";
					StringBuilder sb3 = new StringBuilder();
					for (int k = 0; k < edges_da_rev.size(); k++) {

						sb3.append(edges_da_rev.get(k).toString());
						sb3.append("\t");
					}
					info2 = info2 + sb3.toString();

					// mainLog.println(info2);
				}

			}

		}
		int invMap[] = new int[mdpProd_actionCopies.getNumStates()];
		for (int i = 0; i < invMap.length; i++)
			invMap[i] = i;

		// generate acceptance for the product model by lifting
		AcceptanceOmega acceptance = da.getAcceptance();
		LTLModelChecker.LTLProduct<MDP> a = mcLtl.new LTLProduct<MDP>((MDP) mdpProd_actionCopies, (MDP) model,
				acceptance, da.size(), invMap);
		// a.setAcceptance(a.liftAcceptance(a,da.getAcceptance()));
		// LTLModelChecker.LTLProduct<MDP> a = new LTLModelChecker(null).new
		// LTLProduct<MDP>(null, null, null, 0, invMap);
		mdpProd.exportToDotFile(saveplace + "prod_oo.dot");
		mdpProd_actionCopies.exportToDotFile(saveplace + "prod_ac.dot");

		kutta = mcLtl.constructProductModel(wda, (MDP) model, labelBS, bsInit);
		product = mcLtl.constructProductModel(da, (MDP) model, labelBS, bsInit);

		mainLog.println("printing product mdp ");

		// Find accepting states + compute reachability probabilities
		BitSet acc;
		if (product.getAcceptance() instanceof AcceptanceReach) {
			mainLog.println("\nSkipping accepting MEC computation since acceptance is defined via goal states...");
			acc = ((AcceptanceReach) product.getAcceptance()).getGoalStates();
		} else {
			mainLog.println("\nFinding accepting MECs...");
			acc = mcLtl.findAcceptingECStates(product.getProductModel(), product.getAcceptance());
		}
		if (product.getProductModel().getNumStates() > 10000) {
			mainLog.println("\nChanging product to MDPSparse...");
			productMdp = new MDPSparse((MDPSimple) product.getProductModel());
			kuttaykabacha = new MDPSparse((MDPSimple) kutta.getProductModel());
		} else {
			productMdp = (MDP) product.getProductModel();
			kuttaykabacha = (MDP) kutta.getProductModel();
		}

		BitSet acc2;
		if (a.getAcceptance() instanceof AcceptanceReach) {
			mainLog.println("\nSkipping accepting MEC computation since acceptance is defined via goal states...");
			acc2 = ((AcceptanceReach) a.getAcceptance()).getGoalStates();
		} else {
			mainLog.println("\nFinding accepting MECs...");
			acc2 = new LTLModelChecker(null).findAcceptingECStates(a.getProductModel(), a.getAcceptance());
		}
		if (a.getProductModel().getNumStates() > 10000) {
			mainLog.println("\nChanging product to MDPSparse...");
			kuttaykabacha = new MDPSparse((MDPSimple) a.getProductModel());
		} else {

			kuttaykabacha = (MDP) a.getProductModel();
		}

		// Output product, if required
		if (getExportProductTrans()) {
			mainLog.println(
					"\nExporting product transition matrix to file \"" + getExportProductTransFilename() + "\"...");
			product.getProductModel().exportToPrismExplicitTra(getExportProductTransFilename());
			kutta.getProductModel().exportToPrismExplicitTra(new File(saveplace + "kutta.tra"));
			kutta.getProductModel().exportToDotFile(saveplace + "kutta.dot");
		}
		if (getExportProductStates()) {
			mainLog.println("\nExporting product state space to file \"" + getExportProductStatesFilename() + "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductStatesFilename());
			PrismFileLog den = new PrismFileLog(saveplace + "kutt.sta");
			VarList newVarList = (VarList) modulesFile.createVarList().clone();
			String daVar = "_da";
			while (newVarList.getIndex(daVar) != -1) {
				daVar = "_" + daVar;
			}
			newVarList.addVar(0, new Declaration(daVar, new DeclarationIntUnbounded()), 1, null);
			kutta.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, den);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, out);
			out.close();
			den.close();
		}

		productMdp.exportToDotFile(saveplace + "prod.dot");
		kuttaykabacha.exportToDotFile(saveplace + "kuttaykabacha.dot");

		MDPRewards prodCosts = ((MDPRewards) costsModel).liftFromModel(product);
		computeValueIterWS((MDP) productMdp, prodCosts);
		return test;

	}
	protected StateValues trySkipping(Model model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException {
		StateValues test = null;
		LTLModelChecker mcLtl;
		StateValues probsProduct, probs, costsProduct, costs, rewsProduct, rews;
		MDPModelChecker mcProduct;
		LTLModelChecker.LTLProduct<MDP> product;
		MDP productMdp;
		DA<BitSet, ? extends AcceptanceOmega> da;
		Vector<BitSet> labelBS;
		String saveplace = "/home/fatma/hubic/phD/work/code/mdpltl/prism-svn/prism/tests/fatma_tests/dotfiles/";

		// For LTL model checking routines
		mcLtl = new LTLModelChecker(this);

		ExpressionReward exprRew = (ExpressionReward) expr.getOperand(0);
		Expression ltl = exprRew.getExpression();
		RewardStruct costStruct = exprRew.getRewardStructByIndexObject(modulesFile, modulesFile.getConstantValues());
		int r = exprRew.getRewardStructIndexByIndexObject(modelInfo, constantValues);
		Rewards costsModel = constructRewards(model, costStruct);

		// build DFA
		AcceptanceType[] allowedAcceptance = { AcceptanceType.RABIN, AcceptanceType.REACH };
		labelBS = new Vector<BitSet>();
		da = mcLtl.constructDAForLTLFormula(this, model, ltl, labelBS, allowedAcceptance);

		mainLog.println("printing model ");
		model.exportToDotFile(saveplace + "mdp.dot");
		mainLog.println("printing da ");
		// File out1 = new File(saveplace+"da.dot");
		PrintStream dadot;
		try {
			dadot = new PrintStream(new File(saveplace + "da.dot"));
			da.printDot(dadot);
			dadot.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (!(da.getAcceptance() instanceof AcceptanceReach)) {
			mainLog.println("\nAutomaton is not a DFA. Breaking.");
			// Dummy return vector
			return new StateValues(TypeInt.getInstance(), model);
		}
		// build product
		int numStates = model.getNumStates();
		BitSet bsInit = new BitSet(numStates);
		for (int i = 0; i < numStates; i++) {
			bsInit.set(i, model.isInitialState(i));
		}
		MDPRewardsSimple skipcosts = null;
		MDPRewardsSimple prodcosts = null;
		Object arr[] = mcLtl.constructWProductModel(da,(MDP) model, labelBS, bsInit);
		product =(LTLProduct<MDP>) arr[0]; 
		skipcosts = (MDPRewardsSimple) arr[1];
		prodcosts = (MDPRewardsSimple) arr[2];

		
		mainLog.println("printing product mdp ");

		// Find accepting states + compute reachability probabilities
		
		BitSet acc;
		if (product.getAcceptance() instanceof AcceptanceReach) {
			mainLog.println("\nSkipping accepting MEC computation since acceptance is defined via goal states...");
			acc = ((AcceptanceReach) product.getAcceptance()).getGoalStates();
		} else {
			mainLog.println("\nFinding accepting MECs...");
			acc = new LTLModelChecker(null).findAcceptingECStates(product.getProductModel(), product.getAcceptance());
		}
		
		//TODO create a varlist in the product mdp stuff 
		mcProduct = new MDPModelChecker(this);
		mcProduct.inheritSettings(this);
		
		MDPRewards[] rewards = new MDPRewards[2]; 
		rewards[0]= skipcosts;
		//MDPRewards prodCosts = ((MDPRewards) costsModel).liftFromModel(product);
		//rewards[1]=prodCosts;
		rewards[1]=prodcosts;
		
		if (product.getProductModel().getNumStates() > 10000) {
			mainLog.println("\nChanging product to MDPSparse...");
			productMdp = new MDPSparse((MDPSimple) product.getProductModel());
		} else {

			productMdp = (MDP) product.getProductModel();
		}
//		computeValueIterWS(productMdp,rewards[0]);
		
		BitSet progStates = progressionTrim(product, (MDPRewardsSimple) rewards[0], (MDPRewardsSimple) rewards[1]);

		// Output product, if required
		if (getExportProductTrans()) {
			mainLog.println(
					"\nExporting product transition matrix to file \"" + getExportProductTransFilename() + "\"...");
			product.getProductModel().exportToPrismExplicitTra(getExportProductTransFilename());
			product.getProductModel().exportToPrismExplicitTra(new File(saveplace + "kutta.tra"));
			product.getProductModel().exportToDotFile(saveplace + "kutta.dot");
		}
		if (getExportProductStates()) {
			mainLog.println("\nExporting product state space to file \"" + getExportProductStatesFilename() + "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductStatesFilename());
			PrismFileLog den = new PrismFileLog(saveplace + "kutt.sta");
			VarList newVarList = (VarList) modulesFile.createVarList().clone();
			String daVar = "_da";
			while (newVarList.getIndex(daVar) != -1) {
				daVar = "_" + daVar;
			}
			newVarList.addVar(0, new Declaration(daVar, new DeclarationIntUnbounded()), 1, null);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, den);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, out);
			out.close();
			den.close();
		}

		productMdp.exportToDotFile(saveplace + "prod.dot");
		
		//cheating again 
		BitSet toreach = new BitSet(); 
		toreach.set(8);
	
		ModelCheckerResult res2; //= mcProduct.computeReachRewards(productMdp, skipcosts,toreach , true);
		res2 = mcProduct.computeReachRewards(productMdp, skipcosts,acc , true);
		ModelCheckerPartialSatResult res = mcProduct.computeNestedValIterS(productMdp, acc, rewards[0],rewards[1],
				progStates);	
		probsProduct = StateValues.createFromDoubleArray(res.solnProb, productMdp);
		// Mapping probabilities in the original model
		probs = product.projectToOriginalModel(probsProduct);
		// Get final prob result
		double maxProb = probs.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nMaximum probability to satisfy specification is " + maxProb);

		if (getExportProductVector()) {
			mainLog.println("\nExporting success probabilites over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 1) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 1));
			probsProduct.print(out, false, false, false, false);
			out.close();
		}

		rewsProduct = StateValues.createFromDoubleArray(res.solnProg, productMdp);
		rews = product.projectToOriginalModel(rewsProduct);
		double maxRew = rews.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nFor p = " + maxProb
				+ ", the maximum expected cummulative reward to satisfy specification is " + maxRew);

		if (getExportProductVector()) {
			mainLog.println("\nExporting expected progression reward over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 2) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 2));
			rewsProduct.print(out, false, false, false, false);
			out.close();
		}
		costsProduct = StateValues.createFromDoubleArray(res.solnCost, productMdp);
//		costsProduct = StateValues.createFromDoubleArray(res2.soln, productMdp);
		costs = product.projectToOriginalModel(costsProduct);
		double minCost = costs.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nFor p = " + maxProb + ", r = " + +maxRew
				+ " the minimum expected  cummulative cost to satisfy specification is " + minCost);
		// System.out.println("Probability to find objects: " + maxProb);
		// System.out.println("Expected progression reward: " + maxRew);
		// System.out.println("Expected time to execute task: " + minCost);
		// System.out.println("--------------------------------------------------------------");
		if (getExportProductVector()) {
			mainLog.println("\nExporting expected times until no more progression over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 3) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 3));
			costsProduct.print(out, false, false, false, false);
			out.close();
		}

		return costs;


	}

	protected ModelCheckerPartialSatResultVar computeNestedValIterMod(MDP trimProdMdp, BitSet target, MDPRewards[] rewards, BitSet progStates) throws PrismException {
		
		int i, n, iters, numYes, numNo;
		double initValProb, initValRew, initValCost;
		double solnProb[], soln2Prob[];
		double solnProg[], soln2Prog[];
		double solnCost[], soln2Cost[];
		boolean done;
		BitSet no, yes, unknown;
		long timerVI, timerProb0, timerProb1, timerGlobal;
		int strat[] = null;
		boolean min = false;
		int numRewards = rewards.length;
		ModelCheckerPartialSatResultVar res;
		
		//MDPRewards progRewards = rewards[0];
		//MDPRewards prodCosts = rewards[1];

		timerGlobal = System.currentTimeMillis();

		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		trimProdMdp.checkForDeadlocks(target);

		// Store num states
		n = trimProdMdp.getNumStates();

		// If required, export info about target states
		if (getExportTarget()) {
			BitSet bsInit = new BitSet(n);
			for (i = 0; i < n; i++) {
				bsInit.set(i, trimProdMdp.isInitialState(i));
			}
			List<BitSet> labels = Arrays.asList(bsInit, target);
			List<String> labelNames = Arrays.asList("init", "target");
			mainLog.println("\nExporting target states info to file \"" + getExportTargetFilename() + "\"...");
			PrismLog out = new PrismFileLog(getExportTargetFilename());
			exportLabels(trimProdMdp, labels, labelNames, Prism.EXPORT_PLAIN, out);
			out.close();
		}

		// If required, create/initialise strategy storage
		// Set choices to -1, denoting unknown
		// (except for target states, which are -2, denoting arbitrary)
		if (genStrat || exportAdv) {
			strat = new int[n];
			for (i = 0; i < n; i++) {
				strat[i] = target.get(i) ? -2 : -1;
			}
		}

		// Precomputation
		timerProb0 = System.currentTimeMillis();
		if (precomp && prob0) {
			no = prob0(trimProdMdp, null, target, min, strat);
		} else {
			no = new BitSet();
		}
		timerProb0 = System.currentTimeMillis() - timerProb0;
		timerProb1 = System.currentTimeMillis();
		if (precomp && prob1) {
			yes = prob1(trimProdMdp, null, target, min, strat);
		} else {
			yes = (BitSet) target.clone();
		}
		timerProb1 = System.currentTimeMillis() - timerProb1;

		// Print results of precomputation
		numYes = yes.cardinality();
		numNo = no.cardinality();
		mainLog.println("target=" + target.cardinality() + ", yes=" + numYes + ", no=" + numNo + ", maybe="
				+ (n - (numYes + numNo)));

		// If still required, store strategy for no/yes (0/1) states.
		// This is just for the cases max=0 and min=1, where arbitrary choices
		// suffice (denoted by -2)
		if (genStrat || exportAdv) {
			if (min) {
				for (i = yes.nextSetBit(0); i >= 0; i = yes.nextSetBit(i + 1)) {
					if (!target.get(i))
						strat[i] = -2;
				}
			} else {
				for (i = no.nextSetBit(0); i >= 0; i = no.nextSetBit(i + 1)) {
					strat[i] = -2;
				}
			}
		}

		// Start value iteration
		timerVI = System.currentTimeMillis();
		mainLog.println("Starting prioritised value iteration (" + (min ? "min" : "max") + ")...");

		// Create solution vector(s)
		solnProb = new double[n];
		
		double soln[][] = new double[numRewards][];
		for(int rew=0; rew<numRewards; rew++)
		{
			soln[rew] = new double[n];
		}
		// soln2Prob = new double[n];
		solnProg = new double[n];
		// soln2Prog = new double[n];
		solnCost = new double[n];
		// soln2Cost = new double[n];

		// Initialise solution vectors to initVal
		// where initVal is 0.0 or 1.0, depending on whether we converge from
		// below/above.
		initValProb = 0.0;
		initValRew = 0.0;
		initValCost = 0.0;

		// (valIterDir == ValIterDir.BELOW) ? 0.0 : 1.0;

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(yes);
		unknown.andNot(no);
		for (i = 0; i < n; i++) {
			// solnProb[i] = soln2Prob[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 :
			// initValProb;
			// solnProg[i] = soln2Prog[i] = initValRew;
			// solnCost[i] = soln2Cost[i] = initValCost;
			solnProb[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : initValProb;
			for(int rew = 0; rew<numRewards; rew++)
			{soln[rew][i] = initValRew;
			}
		}

		// Start iterations
		iters = 0;
		done = false;

		int j;
		int numChoices;
		double currentProbVal; //currentProgVal, currentCostVal;
		boolean sameProb;//, sameProg, sameCost;


		double currentRewVals[] = new double[numRewards];
		boolean sameVals[] = new boolean[numRewards];

		while (!done && iters < maxIters) {
			iters++;
			done = true;
			for (i = 0; i < n; i++) {
				if (progStates.get(i)) {
					numChoices = trimProdMdp.getNumChoices(i);
					for (j = 0; j < numChoices; j++) {
						currentProbVal = trimProdMdp.mvMultJacSingle(i, j, solnProb);
						for(int rew=0; rew<numRewards; rew++){
						currentRewVals[rew] = trimProdMdp.mvMultRewSingle(i, j, solnProg, rewards[rew]);
						//currentCostVal = trimProdMdp.mvMultRewSingle(i, j, solnCost, prodCosts);
						}
						sameProb = PrismUtils.doublesAreClose(currentProbVal, solnProb[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						for(int rew=0; rew<numRewards; rew++){
						sameVals[rew] = PrismUtils.doublesAreClose(currentRewVals[rew], solnProg[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						//sameCost = PrismUtils.doublesAreClose(currentCostVal, solnCost[i], termCritParam,
						//		termCrit == TermCrit.ABSOLUTE);
						}
						if (!sameProb && currentProbVal > solnProb[i]) {
							done = false;
							solnProb[i] = currentProbVal;
							for(int rew = 0; rew<numRewards; rew++){
								soln[rew][i]=currentRewVals[rew];
							//solnProg[i] = currentProgVal;
							//solnCost[i] = currentCostVal;
								}
							if (genStrat || exportAdv) {
								strat[i] = j;
							}
						} else {
							if (sameProb) {
								for(int rew=0; rew<numRewards; rew++)
								{
									if(!sameVals[rew]&&currentRewVals[rew]>soln[rew][i])
									{
										done = false; 
										for(int rew2 = rew; rew2<numRewards; rew2++)
										{
											soln[rew][i]=currentRewVals[rew];
										}
										if (genStrat || exportAdv) {
											strat[i] = j;
										break;
									}
								}
//								if (!sameProg && currentProgVal > solnProg[i]) {
//									done = false;
//									// solnProb[i] = currentProbVal;
//									solnProg[i] = currentProgVal;
//									solnCost[i] = currentCostVal;
//									if (genStrat || exportAdv) {
//										strat[i] = j;
//									}
//								} else {
//									if (sameProg) {
//										if (!sameCost && currentCostVal < solnCost[i]) {
//											done = false;
//											// solnProb[i] = currentProbVal;
//											// solnProg[i] = currentProgVal;
//											solnCost[i] = currentCostVal;
//											if (genStrat || exportAdv) {
//												strat[i] = j;
//											}
//										}
//									}
								}
							}
						}
					}
				}
			}
			// Check termination
			// done = PrismUtils.doublesAreClose(solnProb, soln2Prob,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);
			// done = done && PrismUtils.doublesAreClose(solnProg, soln2Prog,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);
			// done = done && PrismUtils.doublesAreClose(solnCost, soln2Cost,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);

			// Save previous iter
			// soln2Prob = solnProb.clone();
			// soln2Prog = solnProg.clone();
			// soln2Cost = solnCost.clone();
		}

		// Finished value iteration
		timerVI = System.currentTimeMillis() - timerVI;
		mainLog.print("Prioritised value iteration (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timerVI / 1000.0 + " seconds.");

		timerGlobal = System.currentTimeMillis() - timerGlobal;
		mainLog.println("Overall policy calculation took  " + timerGlobal / 1000.0 + " seconds.");

		// Non-convergence is an error (usually)
		if (!done && errorOnNonConverge) {
			String msg = "Iterative method did not converge within " + iters + " iterations.";
			msg += "\nConsider using a different numerical method or increasing the maximum number of iterations";
			throw new PrismException(msg);
		}

		res = new ModelCheckerPartialSatResultVar(numRewards);
		// Store strategy
		if (genStrat) {
			res.strat = new MDStrategyArray(trimProdMdp, strat);
		}
		// Export adversary
		if (exportAdv) {
			// Prune strategy
			// restrictStrategyToReachableStates(trimProdMdp, strat);
			// Export
			PrismLog out = new PrismFileLog(exportAdvFilename);
			new DTMCFromMDPMemorylessAdversary(trimProdMdp, strat).exportToPrismExplicitTra(out);
			out.close();
		}

		// Return results
		res.solnProb = solnProb;
		for(int i1=0; i1<res.numRew; i1++)
		res.solns[i1] = soln[i1];
		res.numIters = iters;
		res.timeTaken = timerGlobal / 1000.0;
		return res;
	}




	protected ModelCheckerResult computeValueIterWS(MDP mdp, Rewards cm) {
		ModelCheckerResult res = null;
		int i, n, iters;

		int strat[] = null;

		n = mdp.getNumStates();
		strat = new int[n];

		int j;
		int numChoices;
		double currentProbVal;
		double v[] = new double[n];
		double oldv[] = new double[n];
		double epsilon = 0.00000001;

		boolean done = false;
		for (iters = 0; iters < maxIters; iters++) {
			// starting iters
			double max_res = 0;

			for (j = 0; j < n; j++) {

				double acost = 1000;
				numChoices = mdp.getNumChoices(j);
				for (i = 0; i < numChoices; i++) {
					currentProbVal = mdp.mvMultRewSingle(j, i, v, (MDPRewards) cm);
					if (currentProbVal < acost) {
						acost = currentProbVal;
						strat[j] = i;
					}
				}
				oldv[j] = v[j];
				v[j] = acost;
				if (Math.abs(v[j] - oldv[j]) > max_res) {
					max_res = Math.abs(v[j] - oldv[j]);
				}
			}
			mainLog.print(Arrays.toString(v) + "\n");
			mainLog.print("Policy " + Arrays.toString(strat) + "\n");
			// done = (max_res < epsilon);// PrismUtils.doublesAreClose(v,
			// oldv, epsilon, true);
			done = PrismUtils.doublesAreClose(v, oldv, epsilon, true);
			if (done) {
				mainLog.println("breaking after " + iters + " iterations with max_res = " + epsilon);
				break;
			}
		}

		// }

		return res;

	}

	protected StateValues checkPartialSat(Model model, ExpressionFunc expr, BitSet statesOfInterest)
			throws PrismException {
		LTLModelChecker mcLtl;
		StateValues probsProduct, probs, costsProduct, costs, rewsProduct, rews;
		MDPModelChecker mcProduct;
		LTLModelChecker.LTLProduct<MDP> product;
		MDP productMdp;
		DA<BitSet, ? extends AcceptanceOmega> da;
		Vector<BitSet> labelBS;

		// For LTL model checking routines
		mcLtl = new LTLModelChecker(this);

		// Get LTL spec
		ExpressionReward exprRew = (ExpressionReward) expr.getOperand(0);
		Expression ltl = exprRew.getExpression();
		// System.out.println("--------------------------------------------------------------");
		// //System.out.println("The flat MDP model has " + model.getNumStates()
		// + " states");
		System.out.println("The specification is " + ltl.toString());
		// System.out.println("Generating optimal policy...");
		// System.out.println(" ");

		// Build model costs
		RewardStruct costStruct = exprRew.getRewardStructByIndexObject(modulesFile, modulesFile.getConstantValues());
		mainLog.println("Building cost structure...");
		Rewards costsModel = constructRewards(model, costStruct);

		// build DFA
		AcceptanceType[] allowedAcceptance = { AcceptanceType.RABIN, AcceptanceType.REACH };
		labelBS = new Vector<BitSet>();
		da = mcLtl.constructDAForLTLFormula(this, model, ltl, labelBS, allowedAcceptance);

		if (!(da.getAcceptance() instanceof AcceptanceReach)) {
			mainLog.println("\nAutomaton is not a DFA. Breaking.");
			// Dummy return vector
			return new StateValues(TypeInt.getInstance(), model);
		}
		// calculate distances to accepting states
		long time = System.currentTimeMillis();
		da.setDistancesToAcc();
		time = System.currentTimeMillis() - time;
		mainLog.println("\nAutomaton state distances to an accepting state: " + da.getDistsToAcc());
		mainLog.println("Time for DFA distance to acceptance metric calculation: " + time / 1000.0 + " seconds.");

		// build product
		int numStates = model.getNumStates();
		BitSet bsInit = new BitSet(numStates);
		for (int i = 0; i < numStates; i++) {
			bsInit.set(i, model.isInitialState(i));
		}
		product = mcLtl.constructProductModel(da, (MDP) model, labelBS, bsInit);

		// System.out.println("The product MDP has " +
		// product.getProductModel().getNumStates() + " states");

		// Find accepting states + compute reachability probabilities
		BitSet acc;
		if (product.getAcceptance() instanceof AcceptanceReach) {
			mainLog.println("\nSkipping accepting MEC computation since acceptance is defined via goal states...");
			acc = ((AcceptanceReach) product.getAcceptance()).getGoalStates();
		} else {
			mainLog.println("\nFinding accepting MECs...");
			acc = mcLtl.findAcceptingECStates(product.getProductModel(), product.getAcceptance());
		}

		time = System.currentTimeMillis();
		// Build progression rewards on product
		MDPRewards progRewards = product.liftProgressionFromAutomaton(da.getDistsToAcc());
		time = System.currentTimeMillis() - time;
		mainLog.println(
				"Time for lifting progression reward from automaton to product: " + time / 1000.0 + " seconds.");

		time = System.currentTimeMillis();
		// Build trimmed product costs
		MDPRewards prodCosts = ((MDPRewards) costsModel).liftFromModel(product);
		time = System.currentTimeMillis() - time;
		mainLog.println(
				"Time for lifting cost function from original model to product: " + time / 1000.0 + " seconds.");

		BitSet progStates = progressionTrim(product, (MDPRewardsSimple) progRewards, (MDPRewardsSimple) prodCosts);

		// Output product, if required
		if (getExportProductTrans()) {
			mainLog.println(
					"\nExporting product transition matrix to file \"" + getExportProductTransFilename() + "\"...");
			product.getProductModel().exportToPrismExplicitTra(getExportProductTransFilename());
		}
		if (getExportProductStates()) {
			mainLog.println("\nExporting product state space to file \"" + getExportProductStatesFilename() + "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductStatesFilename());
			VarList newVarList = (VarList) modulesFile.createVarList().clone();
			String daVar = "_da";
			while (newVarList.getIndex(daVar) != -1) {
				daVar = "_" + daVar;
			}
			newVarList.addVar(0, new Declaration(daVar, new DeclarationIntUnbounded()), 1, null);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, out);
			out.close();
		}

		mcProduct = new MDPModelChecker(this);
		mcProduct.inheritSettings(this);

		if (product.getProductModel().getNumStates() > 10000) {
			mainLog.println("\nChanging product to MDPSparse...");
			productMdp = new MDPSparse((MDPSimple) product.getProductModel());
		} else {
			productMdp = (MDP) product.getProductModel();
		}

		mainLog.println("\nComputing reachability probability, expected progression, and expected cost...");
		ModelCheckerPartialSatResult res = mcProduct.computeNestedValIter(productMdp, acc, progRewards, prodCosts,
				progStates);
		probsProduct = StateValues.createFromDoubleArray(res.solnProb, productMdp);
		
		// Mapping probabilities in the original model
		probs = product.projectToOriginalModel(probsProduct);
		// Get final prob result
		double maxProb = probs.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nMaximum probability to satisfy specification is " + maxProb);

		if (getExportProductVector()) {
			mainLog.println("\nExporting success probabilites over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 1) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 1));
			probsProduct.print(out, false, false, false, false);
			out.close();
		}

		rewsProduct = StateValues.createFromDoubleArray(res.solnProg, productMdp);
		rews = product.projectToOriginalModel(rewsProduct);
		double maxRew = rews.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nFor p = " + maxProb
				+ ", the maximum expected cummulative reward to satisfy specification is " + maxRew);

		if (getExportProductVector()) {
			mainLog.println("\nExporting expected progression reward over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 2) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 2));
			rewsProduct.print(out, false, false, false, false);
			out.close();
		}

		costsProduct = StateValues.createFromDoubleArray(res.solnCost, productMdp);
		costs = product.projectToOriginalModel(costsProduct);
		double minCost = costs.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nFor p = " + maxProb + ", r = " + +maxRew
				+ " the minimum expected  cummulative cost to satisfy specification is " + minCost);
		// System.out.println("Probability to find objects: " + maxProb);
		// System.out.println("Expected progression reward: " + maxRew);
		// System.out.println("Expected time to execute task: " + minCost);
		// System.out.println("--------------------------------------------------------------");
		if (getExportProductVector()) {
			mainLog.println("\nExporting expected times until no more progression over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 3) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 3));
			costsProduct.print(out, false, false, false, false);
			out.close();
		}

		return costs;

	}

	protected StateValues checkPartialSatMod(Model model, ExpressionFunc expr, BitSet statesOfInterest)
			throws PrismException {
		LTLModelChecker mcLtl;
		StateValues probsProduct, probs, costsProduct, costs, rewsProduct, rews;
		MDPModelChecker mcProduct;
		LTLModelChecker.LTLProduct<MDP> product;
		MDP productMdp;
		DA<BitSet, ? extends AcceptanceOmega> da;
		Vector<BitSet> labelBS;

		// For LTL model checking routines
		mcLtl = new LTLModelChecker(this);

		// Get LTL spec
		ExpressionReward exprRew = (ExpressionReward) expr.getOperand(0);
		Expression ltl = exprRew.getExpression();
		// System.out.println("--------------------------------------------------------------");
		// //System.out.println("The flat MDP model has " + model.getNumStates()
		// + " states");
		// System.out.println("The specification is " + ltl.toString());
		// System.out.println("Generating optimal policy...");
		// System.out.println(" ");

		// Build model costs
		RewardStruct costStruct = exprRew.getRewardStructByIndexObject(modulesFile, modulesFile.getConstantValues());
		mainLog.println("Building cost structure...");
		Rewards costsModel = constructRewards(model, costStruct);

		// build DFA
		AcceptanceType[] allowedAcceptance = { AcceptanceType.RABIN, AcceptanceType.REACH };
		labelBS = new Vector<BitSet>();
		da = mcLtl.constructDAForLTLFormula(this, model, ltl, labelBS, allowedAcceptance);

		if (!(da.getAcceptance() instanceof AcceptanceReach)) {
			mainLog.println("\nAutomaton is not a DFA. Breaking.");
			// Dummy return vector
			return new StateValues(TypeInt.getInstance(), model);
		}
		// calculate distances to accepting states
		long time = System.currentTimeMillis();
		da.setDistancesToAcc();
		time = System.currentTimeMillis() - time;
		mainLog.println("\nAutomaton state distances to an accepting state: " + da.getDistsToAcc());
		mainLog.println("Time for DFA distance to acceptance metric calculation: " + time / 1000.0 + " seconds.");

		// build product
		int numStates = model.getNumStates();
		BitSet bsInit = new BitSet(numStates);
		for (int i = 0; i < numStates; i++) {
			bsInit.set(i, model.isInitialState(i));
		}
		product = mcLtl.constructProductModel(da, (MDP) model, labelBS, bsInit);

		// System.out.println("The product MDP has " +
		// product.getProductModel().getNumStates() + " states");

		// Find accepting states + compute reachability probabilities
		BitSet acc;
		if (product.getAcceptance() instanceof AcceptanceReach) {
			mainLog.println("\nSkipping accepting MEC computation since acceptance is defined via goal states...");
			acc = ((AcceptanceReach) product.getAcceptance()).getGoalStates();
		} else {
			mainLog.println("\nFinding accepting MECs...");
			acc = mcLtl.findAcceptingECStates(product.getProductModel(), product.getAcceptance());
		}

		time = System.currentTimeMillis();
		// Build progression rewards on product
		MDPRewards progRewards = product.liftProgressionFromAutomaton(da.getDistsToAcc());
		time = System.currentTimeMillis() - time;
		mainLog.println(
				"Time for lifting progression reward from automaton to product: " + time / 1000.0 + " seconds.");

		time = System.currentTimeMillis();
		// Build trimmed product costs
		MDPRewards prodCosts = ((MDPRewards) costsModel).liftFromModel(product);
		time = System.currentTimeMillis() - time;
		mainLog.println(
				"Time for lifting cost function from original model to product: " + time / 1000.0 + " seconds.");

		BitSet progStates = progressionTrim(product, (MDPRewardsSimple) progRewards, (MDPRewardsSimple) prodCosts);

		// Output product, if required
		if (getExportProductTrans()) {
			mainLog.println(
					"\nExporting product transition matrix to file \"" + getExportProductTransFilename() + "\"...");
			product.getProductModel().exportToPrismExplicitTra(getExportProductTransFilename());
		}
		if (getExportProductStates()) {
			mainLog.println("\nExporting product state space to file \"" + getExportProductStatesFilename() + "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductStatesFilename());
			VarList newVarList = (VarList) modulesFile.createVarList().clone();
			String daVar = "_da";
			while (newVarList.getIndex(daVar) != -1) {
				daVar = "_" + daVar;
			}
			newVarList.addVar(0, new Declaration(daVar, new DeclarationIntUnbounded()), 1, null);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, out);
			out.close();
		}

		mcProduct = new MDPModelChecker(this);
		mcProduct.inheritSettings(this);

		if (product.getProductModel().getNumStates() > 10000) {
			mainLog.println("\nChanging product to MDPSparse...");
			productMdp = new MDPSparse((MDPSimple) product.getProductModel());
		} else {
			productMdp = (MDP) product.getProductModel();
		}

		mainLog.println("\nComputing reachability probability, expected progression, and expected cost...");
		MDPRewards[] rewards = new MDPRewards[2]; 
		rewards[0]=progRewards;
		rewards[1]=prodCosts;
		ModelCheckerPartialSatResultVar res = mcProduct.computeNestedValIterMod(productMdp, acc, rewards,
				progStates);
		probsProduct = StateValues.createFromDoubleArray(res.solnProb, productMdp);
		// Mapping probabilities in the original model
		probs = product.projectToOriginalModel(probsProduct);
		// Get final prob result
		double maxProb = probs.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nMaximum probability to satisfy specification is " + maxProb);

		if (getExportProductVector()) {
			mainLog.println("\nExporting success probabilites over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 1) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 1));
			probsProduct.print(out, false, false, false, false);
			out.close();
		}

		rewsProduct = StateValues.createFromDoubleArray(res.solns[0], productMdp);
		rews = product.projectToOriginalModel(rewsProduct);
		double maxRew = rews.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nFor p = " + maxProb
				+ ", the maximum expected cummulative reward to satisfy specification is " + maxRew);

		if (getExportProductVector()) {
			mainLog.println("\nExporting expected progression reward over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 2) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 2));
			rewsProduct.print(out, false, false, false, false);
			out.close();
		}

		costsProduct = StateValues.createFromDoubleArray(res.solns[1], productMdp);
		costs = product.projectToOriginalModel(costsProduct);
		double minCost = costs.getDoubleArray()[model.getFirstInitialState()];
		mainLog.println("\nFor p = " + maxProb + ", r = " + +maxRew
				+ " the minimum expected  cummulative cost to satisfy specification is " + minCost);
		// System.out.println("Probability to find objects: " + maxProb);
		// System.out.println("Expected progression reward: " + maxRew);
		// System.out.println("Expected time to execute task: " + minCost);
		// System.out.println("--------------------------------------------------------------");
		if (getExportProductVector()) {
			mainLog.println("\nExporting expected times until no more progression over product to file \""
					+ PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 3) + "\"...");
			PrismFileLog out = new PrismFileLog(
					PrismUtils.addCounterSuffixToFilename(getExportProductVectorFilename(), 3));
			costsProduct.print(out, false, false, false, false);
			out.close();
		}

		return costs;

	}

	public BitSet progressionTrim(LTLModelChecker.LTLProduct<MDP> product, MDPRewardsSimple progRewards,
			MDPRewardsSimple prodCosts) {
		MDP productModel = product.getProductModel();
		int numStates = productModel.getNumStates();
		List<HashSet<Integer>> predList = new ArrayList<HashSet<Integer>>(numStates);
		Deque<Integer> queue = new ArrayDeque<Integer>();
		BitSet progStates = new BitSet(numStates);
		long time;

		time = System.currentTimeMillis();

		// init predList and queue
		for (int i = 0; i < numStates; i++) {
			predList.add(new HashSet<Integer>());
			for (int j = 0; j < productModel.getNumChoices(i); j++) {
				if (progRewards.getTransitionReward(i, j) > 0.0) {
					queue.add(i);
					progStates.set(i);
					break;
				}
			}
		}

		Iterator<Integer> successorsIt;
		HashSet<Integer> statePreds;
		// set predList
		for (int i = 0; i < numStates; i++) {
			successorsIt = productModel.getSuccessorsIterator(i);
			while (successorsIt.hasNext()) {
				statePreds = predList.get(successorsIt.next());
				statePreds.add(i);
			}
		}

		// set
		int currentState;
		while (!queue.isEmpty()) {
			currentState = queue.poll();
			for (int pred : predList.get(currentState)) {
				if (!progStates.get(pred)) {
					queue.add(pred);
					progStates.set(pred);
				}
			}
		}

		int nTrims = 0;
		// trim rewards according to progression metric TODO: THis can be
		// removed because now we return the progStates
		/*
		 * for(int i = 0; i < numStates; i++) { if(!progStates.get(i)) {
		 * prodCosts.clearRewards(i); nTrims++; } }
		 */

		time = System.currentTimeMillis() - time;
		mainLog.println(
				"\nCleared costs for " + nTrims + " states where no more progression towards goal is possible.");
		mainLog.println("Time for cost trimming: " + time / 1000.0 + " seconds.");
		return progStates;
	}

	@Override
	protected StateValues checkProbPathFormulaLTL(Model model, Expression expr, boolean qual, MinMax minMax,
			BitSet statesOfInterest) throws PrismException {
		LTLModelChecker mcLtl;
		StateValues probsProduct, probs;
		MDPModelChecker mcProduct;
		LTLModelChecker.LTLProduct<MDP> product;

		// For min probabilities, need to negate the formula
		// (add parentheses to allow re-parsing if required)
		if (minMax.isMin()) {
			expr = Expression.Not(Expression.Parenth(expr.deepCopy()));
		}

		// For LTL model checking routines
		mcLtl = new LTLModelChecker(this);

		// Build product of MDP and automaton
		AcceptanceType[] allowedAcceptance = { AcceptanceType.BUCHI, AcceptanceType.RABIN,
				AcceptanceType.GENERALIZED_RABIN, AcceptanceType.REACH };
		product = mcLtl.constructProductMDP(this, (MDP) model, expr, statesOfInterest, allowedAcceptance);

		// Output product, if required
		if (getExportProductTrans()) {
			mainLog.println(
					"\nExporting product transition matrix to file \"" + getExportProductTransFilename() + "\"...");
			product.getProductModel().exportToPrismExplicitTra(getExportProductTransFilename());
		}
		if (getExportProductStates()) {
			mainLog.println("\nExporting product state space to file \"" + getExportProductStatesFilename() + "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductStatesFilename());
			VarList newVarList = (VarList) modulesFile.createVarList().clone();
			String daVar = "_da";
			while (newVarList.getIndex(daVar) != -1) {
				daVar = "_" + daVar;
			}
			newVarList.addVar(0, new Declaration(daVar, new DeclarationIntUnbounded()), 1, null);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, out);
			out.close();
		}

		// Find accepting states + compute reachability probabilities
		BitSet acc;
		if (product.getAcceptance() instanceof AcceptanceReach) {
			mainLog.println("\nSkipping accepting MEC computation since acceptance is defined via goal states...");
			acc = ((AcceptanceReach) product.getAcceptance()).getGoalStates();
		} else {
			mainLog.println("\nFinding accepting MECs...");
			acc = mcLtl.findAcceptingECStates(product.getProductModel(), product.getAcceptance());
		}
		mainLog.println("\nComputing reachability probabilities...");
		mcProduct = new MDPModelChecker(this);
		mcProduct.inheritSettings(this);
		ModelCheckerResult res = mcProduct.computeReachProbs((MDP) product.getProductModel(), acc, false);
		probsProduct = StateValues.createFromDoubleArray(res.soln, product.getProductModel());

		// Subtract from 1 if we're model checking a negated formula for regular
		// Pmin
		if (minMax.isMin()) {
			probsProduct.timesConstant(-1.0);
			probsProduct.plusConstant(1.0);
		}

		// Output vector over product, if required
		if (getExportProductVector()) {
			mainLog.println("\nExporting product solution vector matrix to file \"" + getExportProductVectorFilename()
					+ "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductVectorFilename());
			probsProduct.print(out, false, false, false, false);
			out.close();
		}

		// Mapping probabilities in the original model
		probs = product.projectToOriginalModel(probsProduct);
		probsProduct.clear();

		return probs;
	}

	/**
	 * Compute rewards for a co-safe LTL reward operator.
	 */
	protected StateValues checkRewardCoSafeLTL(Model model, Rewards modelRewards, Expression expr, MinMax minMax,
			BitSet statesOfInterest) throws PrismException {
		LTLModelChecker mcLtl;
		MDPRewards productRewards;
		StateValues rewardsProduct, rewards;
		MDPModelChecker mcProduct;
		LTLModelChecker.LTLProduct<MDP> product;

		// For LTL model checking routines
		mcLtl = new LTLModelChecker(this);

		// Build product of MDP and automaton
		AcceptanceType[] allowedAcceptance = { AcceptanceType.RABIN, AcceptanceType.REACH };
		product = mcLtl.constructProductMDP(this, (MDP) model, expr, statesOfInterest, allowedAcceptance);

		// Adapt reward info to product model
		productRewards = ((MDPRewards) modelRewards).liftFromModel(product);

		// Output product, if required
		if (getExportProductTrans()) {
			mainLog.println(
					"\nExporting product transition matrix to file \"" + getExportProductTransFilename() + "\"...");
			product.getProductModel().exportToPrismExplicitTra(getExportProductTransFilename());
		}
		if (getExportProductStates()) {
			mainLog.println("\nExporting product state space to file \"" + getExportProductStatesFilename() + "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductStatesFilename());
			VarList newVarList = (VarList) modulesFile.createVarList().clone();
			String daVar = "_da";
			while (newVarList.getIndex(daVar) != -1) {
				daVar = "_" + daVar;
			}
			newVarList.addVar(0, new Declaration(daVar, new DeclarationIntUnbounded()), 1, null);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, out);
			out.close();
		}

		// Find accepting states + compute reachability rewards
		BitSet acc;
		if (product.getAcceptance() instanceof AcceptanceReach) {
			// For a DFA, just collect the accept states
			mainLog.println("\nSkipping end component detection since DRA is a DFA...");
			acc = ((AcceptanceReach) product.getAcceptance()).getGoalStates();
		} else {
			// Usually, we have to detect end components in the product
			mainLog.println("\nFinding accepting end components...");
			acc = mcLtl.findAcceptingECStates(product.getProductModel(), product.getAcceptance());
		}
		mainLog.println("\nComputing reachability rewards...");
		mcProduct = new MDPModelChecker(this);
		mcProduct.inheritSettings(this);
		ModelCheckerResult res = mcProduct.computeReachRewards(product.getProductModel(), productRewards, acc,
				minMax.isMin());
		rewardsProduct = StateValues.createFromDoubleArray(res.soln, product.getProductModel());

		// Output vector over product, if required
		if (getExportProductVector()) {
			mainLog.println("\nExporting product solution vector matrix to file \"" + getExportProductVectorFilename()
					+ "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductVectorFilename());
			rewardsProduct.print(out, false, false, false, false);
			out.close();
		}

		// Mapping rewards in the original model
		rewards = product.projectToOriginalModel(rewardsProduct);
		rewardsProduct.clear();

		return rewards;
	}

	// Numerical computation functions

	/**
	 * Compute next=state probabilities. i.e. compute the probability of being
	 * in a state in {@code target} in the next step.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 */
	public ModelCheckerResult computeNextProbs(MDP mdp, BitSet target, boolean min) throws PrismException {
		ModelCheckerResult res = null;
		int n;
		double soln[], soln2[];
		long timer;

		timer = System.currentTimeMillis();

		// Store num states
		n = mdp.getNumStates();

		// Create/initialise solution vector(s)
		soln = Utils.bitsetToDoubleArray(target, n);
		soln2 = new double[n];

		// Next-step probabilities
		mdp.mvMultMinMax(soln, min, soln2, null, false, null);

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln2;
		res.numIters = 1;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Given a value vector x, compute the probability: v(s) = min/max sched [
	 * Sum_s' P_sched(s,s')*x(s') ] for s labeled with a, v(s) = 0 for s not
	 * labeled with a.
	 *
	 * Clears the StateValues object x.
	 *
	 * @param tr
	 *            the transition matrix
	 * @param a
	 *            the set of states labeled with a
	 * @param x
	 *            the value vector
	 * @param min
	 *            compute min instead of max
	 */
	public double[] computeRestrictedNext(MDP mdp, BitSet a, double[] x, boolean min) {
		int n;
		double soln[];

		// Store num states
		n = mdp.getNumStates();

		// initialized to 0.0
		soln = new double[n];

		// Next-step probabilities multiplication
		// restricted to a states
		mdp.mvMultMinMax(x, min, soln, a, false, null);

		return soln;
	}

	/**
	 * Compute reachability probabilities. i.e. compute the min/max probability
	 * of reaching a state in {@code target}.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 */
	public ModelCheckerResult computeReachProbs(MDP mdp, BitSet target, boolean min) throws PrismException {
		return computeReachProbs(mdp, null, target, min, null, null);
	}

	/**
	 * Compute until probabilities. i.e. compute the min/max probability of
	 * reaching a state in {@code target}, while remaining in those in
	 * {@code remain}.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param remain
	 *            Remain in these states (optional: null means "all")
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 */
	public ModelCheckerResult computeUntilProbs(MDP mdp, BitSet remain, BitSet target, boolean min)
			throws PrismException {
		return computeReachProbs(mdp, remain, target, min, null, null);
	}

	/**
	 * Compute reachability/until probabilities. i.e. compute the min/max
	 * probability of reaching a state in {@code target}, while remaining in
	 * those in {@code remain}.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param remain
	 *            Remain in these states (optional: null means "all")
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (may be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values). Also,
	 *            'known' values cannot be passed for some solution methods,
	 *            e.g. policy iteration.
	 */
	public ModelCheckerResult computeReachProbs(MDP mdp, BitSet remain, BitSet target, boolean min, double init[],
			BitSet known) throws PrismException {
		ModelCheckerResult res = null;
		BitSet no, yes;
		int n, numYes, numNo;
		long timer, timerProb0, timerProb1;
		int strat[] = null;
		// Local copy of setting
		MDPSolnMethod mdpSolnMethod = this.mdpSolnMethod;

		// Switch to a supported method, if necessary
		if (mdpSolnMethod == MDPSolnMethod.LINEAR_PROGRAMMING) {
			mdpSolnMethod = MDPSolnMethod.GAUSS_SEIDEL;
			mainLog.printWarning("Switching to MDP solution method \"" + mdpSolnMethod.fullName() + "\"");
		}

		// Check for some unsupported combinations
		if (mdpSolnMethod == MDPSolnMethod.VALUE_ITERATION && valIterDir == ValIterDir.ABOVE) {
			if (!(precomp && prob0))
				throw new PrismException("Precomputation (Prob0) must be enabled for value iteration from above");
			if (!min)
				throw new PrismException("Value iteration from above only works for minimum probabilities");
		}
		if (mdpSolnMethod == MDPSolnMethod.POLICY_ITERATION
				|| mdpSolnMethod == MDPSolnMethod.MODIFIED_POLICY_ITERATION) {
			if (known != null) {
				throw new PrismException("Policy iteration methods cannot be passed 'known' values for some states");
			}
		}

		// Start probabilistic reachability
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting probabilistic reachability (" + (min ? "min" : "max") + ")...");

		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		mdp.checkForDeadlocks(target);

		// Store num states
		n = mdp.getNumStates();

		// Optimise by enlarging target set (if more info is available)
		if (init != null && known != null && !known.isEmpty()) {
			BitSet targetNew = (BitSet) target.clone();
			for (int i : new IterableBitSet(known)) {
				if (init[i] == 1.0) {
					targetNew.set(i);
				}
			}
			target = targetNew;
		}

		// If required, export info about target states
		if (getExportTarget()) {
			BitSet bsInit = new BitSet(n);
			for (int i = 0; i < n; i++) {
				bsInit.set(i, mdp.isInitialState(i));
			}
			List<BitSet> labels = Arrays.asList(bsInit, target);
			List<String> labelNames = Arrays.asList("init", "target");
			mainLog.println("\nExporting target states info to file \"" + getExportTargetFilename() + "\"...");
			exportLabels(mdp, labels, labelNames, Prism.EXPORT_PLAIN, new PrismFileLog(getExportTargetFilename()));
		}

		// If required, create/initialise strategy storage
		// Set choices to -1, denoting unknown
		// (except for target states, which are -2, denoting arbitrary)
		if (genStrat || exportAdv) {
			strat = new int[n];
			for (int i = 0; i < n; i++) {
				strat[i] = target.get(i) ? -2 : -1;
			}
		}

		// Precomputation
		timerProb0 = System.currentTimeMillis();
		if (precomp && prob0) {
			no = prob0(mdp, remain, target, min, strat);
		} else {
			no = new BitSet();
		}
		timerProb0 = System.currentTimeMillis() - timerProb0;
		timerProb1 = System.currentTimeMillis();
		if (precomp && prob1) {
			yes = prob1(mdp, remain, target, min, strat);
		} else {
			yes = (BitSet) target.clone();
		}
		timerProb1 = System.currentTimeMillis() - timerProb1;

		// Print results of precomputation
		numYes = yes.cardinality();
		numNo = no.cardinality();
		mainLog.println("target=" + target.cardinality() + ", yes=" + numYes + ", no=" + numNo + ", maybe="
				+ (n - (numYes + numNo)));

		// If still required, store strategy for no/yes (0/1) states.
		// This is just for the cases max=0 and min=1, where arbitrary choices
		// suffice (denoted by -2)
		if (genStrat || exportAdv) {
			if (min) {
				for (int i = yes.nextSetBit(0); i >= 0; i = yes.nextSetBit(i + 1)) {
					if (!target.get(i))
						strat[i] = -2;
				}
			} else {
				for (int i = no.nextSetBit(0); i >= 0; i = no.nextSetBit(i + 1)) {
					strat[i] = -2;
				}
			}
		}

		// Compute probabilities (if needed)
		if (numYes + numNo < n) {
			switch (mdpSolnMethod) {
			case VALUE_ITERATION:
				res = computeReachProbsValIter(mdp, no, yes, min, init, known, strat);
				break;
			case GAUSS_SEIDEL:
				res = computeReachProbsGaussSeidel(mdp, no, yes, min, init, known, strat);
				break;
			case POLICY_ITERATION:
				res = computeReachProbsPolIter(mdp, no, yes, min, strat);
				break;
			case MODIFIED_POLICY_ITERATION:
				res = computeReachProbsModPolIter(mdp, no, yes, min, strat);
				break;
			default:
				throw new PrismException("Unknown MDP solution method " + mdpSolnMethod.fullName());
			}
		} else {
			res = new ModelCheckerResult();
			res.soln = Utils.bitsetToDoubleArray(yes, n);
		}

		// Finished probabilistic reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Probabilistic reachability took " + timer / 1000.0 + " seconds.");

		// Store strategy
		if (genStrat) {
			res.strat = new MDStrategyArray(mdp, strat);
		}

		// Export adversary
		if (exportAdv) {
			// Prune strategy
			restrictStrategyToReachableStates(mdp, strat);
			// Export
			PrismLog out = new PrismFileLog(exportAdvFilename);
			new DTMCFromMDPMemorylessAdversary(mdp, strat).exportToPrismExplicitTra(out);
			out.close();
		}

		// Update time taken
		res.timeTaken = timer / 1000.0;
		res.timeProb0 = timerProb0 / 1000.0;
		res.timePre = (timerProb0 + timerProb1) / 1000.0;

		return res;
	}

	/**
	 * Prob0 precomputation algorithm. i.e. determine the states of an MDP
	 * which, with min/max probability 0, reach a state in {@code target}, while
	 * remaining in those in {@code remain}. {@code min}=true gives Prob0E,
	 * {@code min}=false gives Prob0A. Optionally, for min only, store optimal
	 * (memoryless) strategy info for 0 states.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param remain
	 *            Remain in these states (optional: null means "all")
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null)
	 */
	public BitSet prob0(MDP mdp, BitSet remain, BitSet target, boolean min, int strat[]) {
		int n, iters;
		BitSet u, soln, unknown;
		boolean u_done;
		long timer;

		// Start precomputation
		timer = System.currentTimeMillis();
		mainLog.println("Starting Prob0 (" + (min ? "min" : "max") + ")...");

		// Special case: no target states
		if (target.cardinality() == 0) {
			soln = new BitSet(mdp.getNumStates());
			soln.set(0, mdp.getNumStates());
			return soln;
		}

		// Initialise vectors
		n = mdp.getNumStates();
		u = new BitSet(n);
		soln = new BitSet(n);

		// Determine set of states actually need to perform computation for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		if (remain != null)
			unknown.and(remain);

		// Fixed point loop
		iters = 0;
		u_done = false;
		// Least fixed point - should start from 0 but we optimise by
		// starting from 'target', thus bypassing first iteration
		u.or(target);
		soln.or(target);
		while (!u_done) {
			iters++;
			// Single step of Prob0
			mdp.prob0step(unknown, u, min, soln);
			// Check termination
			u_done = soln.equals(u);
			// u = soln
			u.clear();
			u.or(soln);
		}

		// Negate
		u.flip(0, n);

		// Finished precomputation
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Prob0 (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// If required, generate strategy. This is for min probs,
		// so it can be done *after* the main prob0 algorithm (unlike for
		// prob1).
		// We simply pick, for all "no" states, the first choice for which all
		// transitions stay in "no"
		if (strat != null) {
			for (int i = u.nextSetBit(0); i >= 0; i = u.nextSetBit(i + 1)) {
				int numChoices = mdp.getNumChoices(i);
				for (int k = 0; k < numChoices; k++) {
					if (mdp.allSuccessorsInSet(i, k, u)) {
						strat[i] = k;
						continue;
					}
				}
			}
		}

		return u;
	}

	/**
	 * Prob1 precomputation algorithm. i.e. determine the states of an MDP
	 * which, with min/max probability 1, reach a state in {@code target}, while
	 * remaining in those in {@code remain}. {@code min}=true gives Prob1A,
	 * {@code min}=false gives Prob1E. Optionally, for max only, store optimal
	 * (memoryless) strategy info for 1 states.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param remain
	 *            Remain in these states (optional: null means "all")
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null)
	 */
	public BitSet prob1(MDP mdp, BitSet remain, BitSet target, boolean min, int strat[]) {
		int n, iters;
		BitSet u, v, soln, unknown;
		boolean u_done, v_done;
		long timer;

		// Start precomputation
		timer = System.currentTimeMillis();
		mainLog.println("Starting Prob1 (" + (min ? "min" : "max") + ")...");

		// Special case: no target states
		if (target.cardinality() == 0) {
			return new BitSet(mdp.getNumStates());
		}

		// Initialise vectors
		n = mdp.getNumStates();
		u = new BitSet(n);
		v = new BitSet(n);
		soln = new BitSet(n);

		// Determine set of states actually need to perform computation for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		if (remain != null)
			unknown.and(remain);

		// Nested fixed point loop
		iters = 0;
		u_done = false;
		// Greatest fixed point
		u.set(0, n);
		while (!u_done) {
			v_done = false;
			// Least fixed point - should start from 0 but we optimise by
			// starting from 'target', thus bypassing first iteration
			v.clear();
			v.or(target);
			soln.clear();
			soln.or(target);
			while (!v_done) {
				iters++;
				// Single step of Prob1
				if (min)
					mdp.prob1Astep(unknown, u, v, soln);
				else
					mdp.prob1Estep(unknown, u, v, soln, null);
				// Check termination (inner)
				v_done = soln.equals(v);
				// v = soln
				v.clear();
				v.or(soln);
			}
			// Check termination (outer)
			u_done = v.equals(u);
			// u = v
			u.clear();
			u.or(v);
		}

		// If we need to generate a strategy, do another iteration of the inner
		// loop for this
		// We could do this during the main double fixed point above, but we
		// would generate surplus
		// strategy info for non-1 states during early iterations of the outer
		// loop,
		// which are not straightforward to remove since this method does not
		// know which states
		// already have valid strategy info from Prob0.
		// Notice that we only need to look at states in u (since we already
		// know the answer),
		// so we restrict 'unknown' further
		unknown.and(u);
		if (!min && strat != null) {
			v_done = false;
			v.clear();
			v.or(target);
			soln.clear();
			soln.or(target);
			while (!v_done) {
				mdp.prob1Estep(unknown, u, v, soln, strat);
				v_done = soln.equals(v);
				v.clear();
				v.or(soln);
			}
			u_done = v.equals(u);
		}

		// Finished precomputation
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Prob1 (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		return u;
	}

	/**
	 * Compute reachability probabilities using value iteration. Optionally,
	 * store optimal (memoryless) strategy info.
	 * 
	 * @param progStates
	 * @param mdp
	 *            The MDP
	 * @param no
	 *            Probability 0 states
	 * @param yes
	 *            Probability 1 states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (will be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null) Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values.
	 */
	protected ModelCheckerPartialSatResult computeNestedValIterS(MDP trimProdMdp, BitSet target, MDPRewards progRewards,
			MDPRewards prodCosts, BitSet progStates) throws PrismException {
		ModelCheckerPartialSatResult res;
		int i, n, iters, numYes, numNo;
		double initValProb, initValRew, initValCost;
		double solnProb[], soln2Prob[];
		double solnProg[], soln2Prog[];
		double solnCost[], soln2Cost[];
		boolean done;
		BitSet no, yes, unknown;
		long timerVI, timerProb0, timerProb1, timerGlobal;
		int strat[] = null;
		boolean min = false;

		timerGlobal = System.currentTimeMillis();

		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		trimProdMdp.checkForDeadlocks(target);

		// Store num states
		n = trimProdMdp.getNumStates();

		// If required, export info about target states
		if (getExportTarget()) {
			BitSet bsInit = new BitSet(n);
			for (i = 0; i < n; i++) {
				bsInit.set(i, trimProdMdp.isInitialState(i));
			}
			List<BitSet> labels = Arrays.asList(bsInit, target);
			List<String> labelNames = Arrays.asList("init", "target");
			mainLog.println("\nExporting target states info to file \"" + getExportTargetFilename() + "\"...");
			PrismLog out = new PrismFileLog(getExportTargetFilename());
			exportLabels(trimProdMdp, labels, labelNames, Prism.EXPORT_PLAIN, out);
			out.close();
		}

		// If required, create/initialise strategy storage
		// Set choices to -1, denoting unknown
		// (except for target states, which are -2, denoting arbitrary)
		if (genStrat || exportAdv) {
			strat = new int[n];
			for (i = 0; i < n; i++) {
				strat[i] = target.get(i) ? -2 : -1;
			}
		}

		// Precomputation
		timerProb0 = System.currentTimeMillis();
		if (precomp && prob0) {
			no = prob0(trimProdMdp, null, target, min, strat);
		} else {
			no = new BitSet();
		}
		timerProb0 = System.currentTimeMillis() - timerProb0;
		timerProb1 = System.currentTimeMillis();
		if (precomp && prob1) {
			yes = prob1(trimProdMdp, null, target, min, strat);
		} else {
			yes = (BitSet) target.clone();
		}
		timerProb1 = System.currentTimeMillis() - timerProb1;

		// Print results of precomputation
		numYes = yes.cardinality();
		numNo = no.cardinality();
		mainLog.println("target=" + target.cardinality() + ", yes=" + numYes + ", no=" + numNo + ", maybe="
				+ (n - (numYes + numNo)));

		// If still required, store strategy for no/yes (0/1) states.
		// This is just for the cases max=0 and min=1, where arbitrary choices
		// suffice (denoted by -2)
		if (genStrat || exportAdv) {
			if (min) {
				for (i = yes.nextSetBit(0); i >= 0; i = yes.nextSetBit(i + 1)) {
					if (!target.get(i))
						strat[i] = -2;
				}
			} else {
				for (i = no.nextSetBit(0); i >= 0; i = no.nextSetBit(i + 1)) {
					strat[i] = -2;
				}
			}
		}

		// Start value iteration
		timerVI = System.currentTimeMillis();
		mainLog.println("Starting prioritised value iteration (" + (min ? "min" : "max") + ")...");

		// Create solution vector(s)
		solnProb = new double[n];
		// soln2Prob = new double[n];
		solnProg = new double[n];
		// soln2Prog = new double[n];
		solnCost = new double[n];
		// soln2Cost = new double[n];

		// Initialise solution vectors to initVal
		// where initVal is 0.0 or 1.0, depending on whether we converge from
		// below/above.
		initValProb = 0.0;
		initValRew = 0.0;
		initValCost = 0.0;

		// (valIterDir == ValIterDir.BELOW) ? 0.0 : 1.0;

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(yes);
		unknown.andNot(no);
		for (i = 0; i < n; i++) {
			// solnProb[i] = soln2Prob[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 :
			// initValProb;
			// solnProg[i] = soln2Prog[i] = initValRew;
			// solnCost[i] = soln2Cost[i] = initValCost;
			//soln[i] = soln2[i] = target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : 0.0;
			solnProb[i] = target.get(i) ? 1.0 : no.get(i) ? 0.0 : initValProb;
//			solnProg[i] =  target.get(i) ? 0.0 : no.get(i) ? Double.POSITIVE_INFINITY : 0.0;//1000;//initValRew;//target.get(i) ? 0.0 : no.get(i) ? Double.POSITIVE_INFINITY : 0.0;
			solnProg[i] = initValRew;
			solnCost[i] = initValCost;
		}

		// Start iterations
		iters = 0;
		done = false;

		int j;
		int numChoices;
		double currentProbVal, currentProgVal, currentCostVal;
		boolean sameProb, sameProg, sameCost;

		while (!done && iters < maxIters) {
			iters++;
			done = true;
			for (i = 0; i < n; i++) {
				if (progStates.get(i)) {
					numChoices = trimProdMdp.getNumChoices(i);
					for (j = 0; j < numChoices; j++) {
						currentProbVal = trimProdMdp.mvMultJacSingle(i, j, solnProb);
						currentProgVal = trimProdMdp.mvMultRewSingle(i, j, solnProg, progRewards);
						currentCostVal = trimProdMdp.mvMultRewSingle(i, j, solnCost, prodCosts);
						sameProb = PrismUtils.doublesAreClose(currentProbVal, solnProb[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						sameProg = PrismUtils.doublesAreClose(currentProgVal, solnProg[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						sameCost = PrismUtils.doublesAreClose(currentCostVal, solnCost[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						if (!sameProb && currentProbVal > solnProb[i]) {
							done = false;
							solnProb[i] = currentProbVal;
							solnProg[i] = currentProgVal;
							solnCost[i] = currentCostVal;
							if (genStrat || exportAdv) {
								strat[i] = j;
							}
						} else {
							if (sameProb) {
								if (!sameProg && currentProgVal < solnProg[i]) {
									done = false;
									// solnProb[i] = currentProbVal;
									solnProg[i] = currentProgVal;
									solnCost[i] = currentCostVal;
									if (genStrat || exportAdv) {
										strat[i] = j;
									}
								} else {
									if (sameProg) {
										if (!sameCost && currentCostVal < solnCost[i]) {
											done = false;
											// solnProb[i] = currentProbVal;
											// solnProg[i] = currentProgVal;
											solnCost[i] = currentCostVal;
											if (genStrat || exportAdv) {
												strat[i] = j;
											}
										}
									}
								}
							}
						}
					}
				}
			}
			// Check termination
			// done = PrismUtils.doublesAreClose(solnProb, soln2Prob,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);
			// done = done && PrismUtils.doublesAreClose(solnProg, soln2Prog,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);
			// done = done && PrismUtils.doublesAreClose(solnCost, soln2Cost,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);

			// Save previous iter
			// soln2Prob = solnProb.clone();
			// soln2Prog = solnProg.clone();
			// soln2Cost = solnCost.clone();
		}

		// Finished value iteration
		timerVI = System.currentTimeMillis() - timerVI;
		mainLog.print("Prioritised value iteration (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timerVI / 1000.0 + " seconds.");

		timerGlobal = System.currentTimeMillis() - timerGlobal;
		mainLog.println("Overall policy calculation took  " + timerGlobal / 1000.0 + " seconds.");

		// Non-convergence is an error (usually)
		if (!done && errorOnNonConverge) {
			String msg = "Iterative method did not converge within " + iters + " iterations.";
			msg += "\nConsider using a different numerical method or increasing the maximum number of iterations";
			throw new PrismException(msg);
		}

		res = new ModelCheckerPartialSatResult();
		// Store strategy
		if (genStrat) {
			res.strat = new MDStrategyArray(trimProdMdp, strat);
		}
		// Export adversary
		if (exportAdv) {
			// Prune strategy
			// restrictStrategyToReachableStates(trimProdMdp, strat);
			// Export
			PrismLog out = new PrismFileLog(exportAdvFilename);
			new DTMCFromMDPMemorylessAdversary(trimProdMdp, strat).exportToPrismExplicitTra(out);
			out.close();
		}

		// Return results
		res.solnProb = solnProb;
		res.solnProg = solnProg;
		res.solnCost = solnCost;
		res.numIters = iters;
		res.timeTaken = timerGlobal / 1000.0;
		return res;
	}

	/**
	 * Compute reachability probabilities using value iteration. Optionally,
	 * store optimal (memoryless) strategy info.
	 * 
	 * @param progStates
	 * @param mdp
	 *            The MDP
	 * @param no
	 *            Probability 0 states
	 * @param yes
	 *            Probability 1 states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (will be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null) Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values.
	 */
	protected ModelCheckerPartialSatResult computeNestedValIter(MDP trimProdMdp, BitSet target, MDPRewards progRewards,
			MDPRewards prodCosts, BitSet progStates) throws PrismException {
		ModelCheckerPartialSatResult res;
		int i, n, iters, numYes, numNo;
		double initValProb, initValRew, initValCost;
		double solnProb[], soln2Prob[];
		double solnProg[], soln2Prog[];
		double solnCost[], soln2Cost[];
		boolean done;
		BitSet no, yes, unknown;
		long timerVI, timerProb0, timerProb1, timerGlobal;
		int strat[] = null;
		boolean min = false;

		timerGlobal = System.currentTimeMillis();

		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		trimProdMdp.checkForDeadlocks(target);

		// Store num states
		n = trimProdMdp.getNumStates();

		// If required, export info about target states
		if (getExportTarget()) {
			BitSet bsInit = new BitSet(n);
			for (i = 0; i < n; i++) {
				bsInit.set(i, trimProdMdp.isInitialState(i));
			}
			List<BitSet> labels = Arrays.asList(bsInit, target);
			List<String> labelNames = Arrays.asList("init", "target");
			mainLog.println("\nExporting target states info to file \"" + getExportTargetFilename() + "\"...");
			PrismLog out = new PrismFileLog(getExportTargetFilename());
			exportLabels(trimProdMdp, labels, labelNames, Prism.EXPORT_PLAIN, out);
			out.close();
		}

		// If required, create/initialise strategy storage
		// Set choices to -1, denoting unknown
		// (except for target states, which are -2, denoting arbitrary)
		if (genStrat || exportAdv) {
			strat = new int[n];
			for (i = 0; i < n; i++) {
				strat[i] = target.get(i) ? -2 : -1;
			}
		}

		// Precomputation
		timerProb0 = System.currentTimeMillis();
		if (precomp && prob0) {
			no = prob0(trimProdMdp, null, target, min, strat);
		} else {
			no = new BitSet();
		}
		timerProb0 = System.currentTimeMillis() - timerProb0;
		timerProb1 = System.currentTimeMillis();
		if (precomp && prob1) {
			yes = prob1(trimProdMdp, null, target, min, strat);
		} else {
			yes = (BitSet) target.clone();
		}
		timerProb1 = System.currentTimeMillis() - timerProb1;

		// Print results of precomputation
		numYes = yes.cardinality();
		numNo = no.cardinality();
		mainLog.println("target=" + target.cardinality() + ", yes=" + numYes + ", no=" + numNo + ", maybe="
				+ (n - (numYes + numNo)));

		// If still required, store strategy for no/yes (0/1) states.
		// This is just for the cases max=0 and min=1, where arbitrary choices
		// suffice (denoted by -2)
		if (genStrat || exportAdv) {
			if (min) {
				for (i = yes.nextSetBit(0); i >= 0; i = yes.nextSetBit(i + 1)) {
					if (!target.get(i))
						strat[i] = -2;
				}
			} else {
				for (i = no.nextSetBit(0); i >= 0; i = no.nextSetBit(i + 1)) {
					strat[i] = -2;
				}
			}
		}

		// Start value iteration
		timerVI = System.currentTimeMillis();
		mainLog.println("Starting prioritised value iteration (" + (min ? "min" : "max") + ")...");

		// Create solution vector(s)
		solnProb = new double[n];
		// soln2Prob = new double[n];
		solnProg = new double[n];
		// soln2Prog = new double[n];
		solnCost = new double[n];
		// soln2Cost = new double[n];

		// Initialise solution vectors to initVal
		// where initVal is 0.0 or 1.0, depending on whether we converge from
		// below/above.
		initValProb = 0.0;
		initValRew = 0.0;
		initValCost = 0.0;

		// (valIterDir == ValIterDir.BELOW) ? 0.0 : 1.0;

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(yes);
		unknown.andNot(no);
		for (i = 0; i < n; i++) {
			// solnProb[i] = soln2Prob[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 :
			// initValProb;
			// solnProg[i] = soln2Prog[i] = initValRew;
			// solnCost[i] = soln2Cost[i] = initValCost;
			solnProb[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : initValProb;
			solnProg[i] = initValRew;
			solnCost[i] = initValCost;
		}

		// Start iterations
		iters = 0;
		done = false;

		int j;
		int numChoices;
		double currentProbVal, currentProgVal, currentCostVal;
		boolean sameProb, sameProg, sameCost;

		while (!done && iters < maxIters) {
			iters++;
			done = true;
			for (i = 0; i < n; i++) {
				if (progStates.get(i)) {
					numChoices = trimProdMdp.getNumChoices(i);
					for (j = 0; j < numChoices; j++) {
						currentProbVal = trimProdMdp.mvMultJacSingle(i, j, solnProb);
						currentProgVal = trimProdMdp.mvMultRewSingle(i, j, solnProg, progRewards);
						currentCostVal = trimProdMdp.mvMultRewSingle(i, j, solnCost, prodCosts);
						sameProb = PrismUtils.doublesAreClose(currentProbVal, solnProb[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						sameProg = PrismUtils.doublesAreClose(currentProgVal, solnProg[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						sameCost = PrismUtils.doublesAreClose(currentCostVal, solnCost[i], termCritParam,
								termCrit == TermCrit.ABSOLUTE);
						if (!sameProb && currentProbVal > solnProb[i]) {
							done = false;
							solnProb[i] = currentProbVal;
							solnProg[i] = currentProgVal;
							solnCost[i] = currentCostVal;
							if (genStrat || exportAdv) {
								strat[i] = j;
							}
						} else {
							if (sameProb) {
								if (!sameProg && currentProgVal > solnProg[i]) {
									done = false;
									// solnProb[i] = currentProbVal;
									solnProg[i] = currentProgVal;
									solnCost[i] = currentCostVal;
									if (genStrat || exportAdv) {
										strat[i] = j;
									}
								} else {
									if (sameProg) {
										if (!sameCost && currentCostVal < solnCost[i]) {
											done = false;
											// solnProb[i] = currentProbVal;
											// solnProg[i] = currentProgVal;
											solnCost[i] = currentCostVal;
											if (genStrat || exportAdv) {
												strat[i] = j;
											}
										}
									}
								}
							}
						}
					}
				}
			}
			// Check termination
			// done = PrismUtils.doublesAreClose(solnProb, soln2Prob,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);
			// done = done && PrismUtils.doublesAreClose(solnProg, soln2Prog,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);
			// done = done && PrismUtils.doublesAreClose(solnCost, soln2Cost,
			// termCritParam, termCrit == TermCrit.ABSOLUTE);

			// Save previous iter
			// soln2Prob = solnProb.clone();
			// soln2Prog = solnProg.clone();
			// soln2Cost = solnCost.clone();
		}

		// Finished value iteration
		timerVI = System.currentTimeMillis() - timerVI;
		mainLog.print("Prioritised value iteration (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timerVI / 1000.0 + " seconds.");

		timerGlobal = System.currentTimeMillis() - timerGlobal;
		mainLog.println("Overall policy calculation took  " + timerGlobal / 1000.0 + " seconds.");

		// Non-convergence is an error (usually)
		if (!done && errorOnNonConverge) {
			String msg = "Iterative method did not converge within " + iters + " iterations.";
			msg += "\nConsider using a different numerical method or increasing the maximum number of iterations";
			throw new PrismException(msg);
		}

		res = new ModelCheckerPartialSatResult();
		// Store strategy
		if (genStrat) {
			res.strat = new MDStrategyArray(trimProdMdp, strat);
		}
		// Export adversary
		if (exportAdv) {
			// Prune strategy
			// restrictStrategyToReachableStates(trimProdMdp, strat);
			// Export
			PrismLog out = new PrismFileLog(exportAdvFilename);
			new DTMCFromMDPMemorylessAdversary(trimProdMdp, strat).exportToPrismExplicitTra(out);
			out.close();
		}

		// Return results
		res.solnProb = solnProb;
		res.solnProg = solnProg;
		res.solnCost = solnCost;
		res.numIters = iters;
		res.timeTaken = timerGlobal / 1000.0;
		return res;
	}

	/**
	 * Compute reachability probabilities using value iteration. Optionally,
	 * store optimal (memoryless) strategy info.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param no
	 *            Probability 0 states
	 * @param yes
	 *            Probability 1 states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (will be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null) Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values.
	 */
	protected ModelCheckerResult computeReachProbsValIter(MDP mdp, BitSet no, BitSet yes, boolean min, double init[],
			BitSet known, int strat[]) throws PrismException {
		ModelCheckerResult res;
		BitSet unknown;
		int i, n, iters;
		double soln[], soln2[], tmpsoln[], initVal;
		boolean done;
		long timer;
		// Start value iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting value iteration (" + (min ? "min" : "max") + ")...");

		// Store num states
		n = mdp.getNumStates();

		// Create solution vector(s)
		soln = new double[n];
		soln2 = (init == null) ? new double[n] : init;

		// Initialise solution vectors. Use (where available) the following in
		// order of preference:
		// (1) exact answer, if already known; (2) 1.0/0.0 if in yes/no; (3)
		// passed in initial value; (4) initVal
		// where initVal is 0.0 or 1.0, depending on whether we converge from
		// below/above.
		initVal = (valIterDir == ValIterDir.BELOW) ? 0.0 : 1.0;
		if (init != null) {
			if (known != null) {
				for (i = 0; i < n; i++)
					soln[i] = soln2[i] = known.get(i) ? init[i] : yes.get(i) ? 1.0 : no.get(i) ? 0.0 : init[i];
			} else {
				for (i = 0; i < n; i++)
					soln[i] = soln2[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : init[i];
			}
		} else {
			for (i = 0; i < n; i++)
				soln[i] = soln2[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : initVal;
		}

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(yes);
		unknown.andNot(no);
		if (known != null)
			unknown.andNot(known);

		// Start iterations
		iters = 0;
		done = false;
		while (!done && iters < maxIters) {
			iters++;
			// Matrix-vector multiply and min/max ops
			mdp.mvMultMinMax(soln, min, soln2, unknown, false, strat);
			// Check termination
			done = PrismUtils.doublesAreClose(soln, soln2, termCritParam, termCrit == TermCrit.ABSOLUTE);
			// Swap vectors for next iter
			tmpsoln = soln;
			soln = soln2;
			soln2 = tmpsoln;
		}

		// Finished value iteration
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Value iteration (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// Non-convergence is an error (usually)
		if (!done && errorOnNonConverge) {
			String msg = "Iterative method did not converge within " + iters + " iterations.";
			msg += "\nConsider using a different numerical method or increasing the maximum number of iterations";
			throw new PrismException(msg);
		}

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Compute reachability probabilities using Gauss-Seidel (including
	 * Jacobi-style updates).
	 * 
	 * @param mdp
	 *            The MDP
	 * @param no
	 *            Probability 0 states
	 * @param yes
	 *            Probability 1 states
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (will be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null) Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values.
	 */
	protected ModelCheckerResult computeReachProbsGaussSeidel(MDP mdp, BitSet no, BitSet yes, boolean min,
			double init[], BitSet known, int strat[]) throws PrismException {
		ModelCheckerResult res;
		BitSet unknown;
		int i, n, iters;
		double soln[], initVal, maxDiff;
		boolean done;
		long timer;

		// Start value iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting Gauss-Seidel (" + (min ? "min" : "max") + ")...");

		// Store num states
		n = mdp.getNumStates();

		// Create solution vector
		soln = (init == null) ? new double[n] : init;

		// Initialise solution vector. Use (where available) the following in
		// order of preference:
		// (1) exact answer, if already known; (2) 1.0/0.0 if in yes/no; (3)
		// passed in initial value; (4) initVal
		// where initVal is 0.0 or 1.0, depending on whether we converge from
		// below/above.
		initVal = (valIterDir == ValIterDir.BELOW) ? 0.0 : 1.0;
		if (init != null) {
			if (known != null) {
				for (i = 0; i < n; i++)
					soln[i] = known.get(i) ? init[i] : yes.get(i) ? 1.0 : no.get(i) ? 0.0 : init[i];
			} else {
				for (i = 0; i < n; i++)
					soln[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : init[i];
			}
		} else {
			for (i = 0; i < n; i++)
				soln[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : initVal;
		}

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(yes);
		unknown.andNot(no);
		if (known != null)
			unknown.andNot(known);

		// Start iterations
		iters = 0;
		done = false;
		while (!done && iters < maxIters) {
			iters++;
			// Matrix-vector multiply
			maxDiff = mdp.mvMultGSMinMax(soln, min, unknown, false, termCrit == TermCrit.ABSOLUTE, strat);
			// Check termination
			done = maxDiff < termCritParam;
		}

		// Finished Gauss-Seidel
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Gauss-Seidel");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// Non-convergence is an error (usually)
		if (!done && errorOnNonConverge) {
			String msg = "Iterative method did not converge within " + iters + " iterations.";
			msg += "\nConsider using a different numerical method or increasing the maximum number of iterations";
			throw new PrismException(msg);
		}

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Compute reachability probabilities using policy iteration. Optionally,
	 * store optimal (memoryless) strategy info.
	 * 
	 * @param mdp:
	 *            The MDP
	 * @param no:
	 *            Probability 0 states
	 * @param yes:
	 *            Probability 1 states
	 * @param min:
	 *            Min or max probabilities (true=min, false=max)
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null)
	 */
	protected ModelCheckerResult computeReachProbsPolIter(MDP mdp, BitSet no, BitSet yes, boolean min, int strat[])
			throws PrismException {
		ModelCheckerResult res;
		int i, n, iters, totalIters;
		double soln[], soln2[];
		boolean done;
		long timer;
		DTMCModelChecker mcDTMC;
		DTMC dtmc;

		// Re-use solution to solve each new policy (strategy)?
		boolean reUseSoln = true;

		// Start policy iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting policy iteration (" + (min ? "min" : "max") + ")...");

		// Create a DTMC model checker (for solving policies)
		mcDTMC = new DTMCModelChecker(this);
		mcDTMC.inheritSettings(this);
		mcDTMC.setLog(new PrismDevNullLog());

		// Store num states
		n = mdp.getNumStates();

		// Create solution vectors
		soln = new double[n];
		soln2 = new double[n];

		// Initialise solution vectors.
		for (i = 0; i < n; i++)
			soln[i] = soln2[i] = yes.get(i) ? 1.0 : 0.0;

		// If not passed in, create new storage for strategy and initialise
		// Initial strategy just picks first choice (0) everywhere
		if (strat == null) {
			strat = new int[n];
			for (i = 0; i < n; i++)
				strat[i] = 0;
		}
		// Otherwise, just initialise for states not in yes/no
		// (Optimal choices for yes/no should already be known)
		else {
			for (i = 0; i < n; i++)
				if (!(no.get(i) || yes.get(i)))
					strat[i] = 0;
		}

		// Start iterations
		iters = totalIters = 0;
		done = false;
		while (!done) {
			iters++;
			// Solve induced DTMC for strategy
			dtmc = new DTMCFromMDPMemorylessAdversary(mdp, strat);
			res = mcDTMC.computeReachProbsGaussSeidel(dtmc, no, yes, reUseSoln ? soln : null, null);
			soln = res.soln;
			totalIters += res.numIters;
			// Check if optimal, improve non-optimal choices
			mdp.mvMultMinMax(soln, min, soln2, null, false, null);
			done = true;
			for (i = 0; i < n; i++) {
				// Don't look at no/yes states - we may not have strategy info
				// for them,
				// so they might appear non-optimal
				if (no.get(i) || yes.get(i))
					continue;
				if (!PrismUtils.doublesAreClose(soln[i], soln2[i], termCritParam, termCrit == TermCrit.ABSOLUTE)) {
					done = false;
					List<Integer> opt = mdp.mvMultMinMaxSingleChoices(i, soln, min, soln2[i]);
					// Only update strategy if strictly better
					if (!opt.contains(strat[i]))
						strat[i] = opt.get(0);
				}
			}
		}

		// Finished policy iteration
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Policy iteration");
		mainLog.println(" took " + iters + " cycles (" + totalIters + " iterations in total) and " + timer / 1000.0
				+ " seconds.");

		// Return results
		// (Note we don't add the strategy - the one passed in is already there
		// and might have some existing choices stored for other states).
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = totalIters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Compute reachability probabilities using modified policy iteration.
	 * 
	 * @param mdp:
	 *            The MDP
	 * @param no:
	 *            Probability 0 states
	 * @param yes:
	 *            Probability 1 states
	 * @param min:
	 *            Min or max probabilities (true=min, false=max)
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null)
	 */
	protected ModelCheckerResult computeReachProbsModPolIter(MDP mdp, BitSet no, BitSet yes, boolean min, int strat[])
			throws PrismException {
		ModelCheckerResult res;
		int i, n, iters, totalIters;
		double soln[], soln2[];
		boolean done;
		long timer;
		DTMCModelChecker mcDTMC;
		DTMC dtmc;

		// Start value iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting modified policy iteration (" + (min ? "min" : "max") + ")...");

		// Create a DTMC model checker (for solving policies)
		mcDTMC = new DTMCModelChecker(this);
		mcDTMC.inheritSettings(this);
		mcDTMC.setLog(new PrismDevNullLog());

		// Limit iters for DTMC solution - this implements "modified" policy
		// iteration
		mcDTMC.setMaxIters(100);
		mcDTMC.setErrorOnNonConverge(false);

		// Store num states
		n = mdp.getNumStates();

		// Create solution vectors
		soln = new double[n];
		soln2 = new double[n];

		// Initialise solution vectors.
		for (i = 0; i < n; i++)
			soln[i] = soln2[i] = yes.get(i) ? 1.0 : 0.0;

		// If not passed in, create new storage for strategy and initialise
		// Initial strategy just picks first choice (0) everywhere
		if (strat == null) {
			strat = new int[n];
			for (i = 0; i < n; i++)
				strat[i] = 0;
		}
		// Otherwise, just initialise for states not in yes/no
		// (Optimal choices for yes/no should already be known)
		else {
			for (i = 0; i < n; i++)
				if (!(no.get(i) || yes.get(i)))
					strat[i] = 0;
		}

		// Start iterations
		iters = totalIters = 0;
		done = false;
		while (!done) {
			iters++;
			// Solve induced DTMC for strategy
			dtmc = new DTMCFromMDPMemorylessAdversary(mdp, strat);
			res = mcDTMC.computeReachProbsGaussSeidel(dtmc, no, yes, soln, null);
			soln = res.soln;
			totalIters += res.numIters;
			// Check if optimal, improve non-optimal choices
			mdp.mvMultMinMax(soln, min, soln2, null, false, null);
			done = true;
			for (i = 0; i < n; i++) {
				// Don't look at no/yes states - we don't store strategy info
				// for them,
				// so they might appear non-optimal
				if (no.get(i) || yes.get(i))
					continue;
				if (!PrismUtils.doublesAreClose(soln[i], soln2[i], termCritParam, termCrit == TermCrit.ABSOLUTE)) {
					done = false;
					List<Integer> opt = mdp.mvMultMinMaxSingleChoices(i, soln, min, soln2[i]);
					strat[i] = opt.get(0);
				}
			}
		}

		// Finished policy iteration
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Modified policy iteration");
		mainLog.println(" took " + iters + " cycles (" + totalIters + " iterations in total) and " + timer / 1000.0
				+ " seconds.");

		// Return results
		// (Note we don't add the strategy - the one passed in is already there
		// and might have some existing choices stored for other states).
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = totalIters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Construct strategy information for min/max reachability probabilities.
	 * (More precisely, list of indices of choices resulting in min/max.) (Note:
	 * indices are guaranteed to be sorted in ascending order.)
	 * 
	 * @param mdp
	 *            The MDP
	 * @param state
	 *            The state to generate strategy info for
	 * @param target
	 *            The set of target states to reach
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param lastSoln
	 *            Vector of values from which to recompute in one iteration
	 */
	public List<Integer> probReachStrategy(MDP mdp, int state, BitSet target, boolean min, double lastSoln[])
			throws PrismException {
		double val = mdp.mvMultMinMaxSingle(state, lastSoln, min, null);
		return mdp.mvMultMinMaxSingleChoices(state, lastSoln, min, val);
	}

	/**
	 * Compute bounded reachability probabilities. i.e. compute the min/max
	 * probability of reaching a state in {@code target} within k steps.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param target
	 *            Target states
	 * @param k
	 *            Bound
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 */
	public ModelCheckerResult computeBoundedReachProbs(MDP mdp, BitSet target, int k, boolean min)
			throws PrismException {
		return computeBoundedReachProbs(mdp, null, target, k, min, null, null);
	}

	/**
	 * Compute bounded until probabilities. i.e. compute the min/max probability
	 * of reaching a state in {@code target}, within k steps, and while
	 * remaining in states in {@code remain}.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param remain
	 *            Remain in these states (optional: null means "all")
	 * @param target
	 *            Target states
	 * @param k
	 *            Bound
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 */
	public ModelCheckerResult computeBoundedUntilProbs(MDP mdp, BitSet remain, BitSet target, int k, boolean min)
			throws PrismException {
		return computeBoundedReachProbs(mdp, remain, target, k, min, null, null);
	}

	/**
	 * Compute bounded reachability/until probabilities. i.e. compute the
	 * min/max probability of reaching a state in {@code target}, within k
	 * steps, and while remaining in states in {@code remain}.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param remain
	 *            Remain in these states (optional: null means "all")
	 * @param target
	 *            Target states
	 * @param k
	 *            Bound
	 * @param min
	 *            Min or max probabilities (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (may be overwritten)
	 * @param results
	 *            Optional array of size k+1 to store (init state) results for
	 *            each step (null if unused)
	 */
	public ModelCheckerResult computeBoundedReachProbs(MDP mdp, BitSet remain, BitSet target, int k, boolean min,
			double init[], double results[]) throws PrismException {
		ModelCheckerResult res = null;
		BitSet unknown;
		int i, n, iters;
		double soln[], soln2[], tmpsoln[];
		long timer;

		// Start bounded probabilistic reachability
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting bounded probabilistic reachability (" + (min ? "min" : "max") + ")...");

		// Store num states
		n = mdp.getNumStates();

		// Create solution vector(s)
		soln = new double[n];
		soln2 = (init == null) ? new double[n] : init;

		// Initialise solution vectors. Use passed in initial vector, if present
		if (init != null) {
			for (i = 0; i < n; i++)
				soln[i] = soln2[i] = target.get(i) ? 1.0 : init[i];
		} else {
			for (i = 0; i < n; i++)
				soln[i] = soln2[i] = target.get(i) ? 1.0 : 0.0;
		}
		// Store intermediate results if required
		// (compute min/max value over initial states for first step)
		if (results != null) {
			// TODO: whether this is min or max should be specified somehow
			results[0] = Utils.minMaxOverArraySubset(soln2, mdp.getInitialStates(), true);
		}

		// Determine set of states actually need to perform computation for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		if (remain != null)
			unknown.and(remain);

		// Start iterations
		iters = 0;
		while (iters < k) {
			iters++;
			// Matrix-vector multiply and min/max ops
			mdp.mvMultMinMax(soln, min, soln2, unknown, false, null);
			// Store intermediate results if required
			// (compute min/max value over initial states for this step)
			if (results != null) {
				// TODO: whether this is min or max should be specified somehow
				results[iters] = Utils.minMaxOverArraySubset(soln2, mdp.getInitialStates(), true);
			}
			// Swap vectors for next iter
			tmpsoln = soln;
			soln = soln2;
			soln2 = tmpsoln;
		}

		// Finished bounded probabilistic reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Bounded probabilistic reachability (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.lastSoln = soln2;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		res.timePre = 0.0;
		return res;
	}

	/**
	 * Compute expected cumulative (step-bounded) rewards. i.e. compute the
	 * min/max reward accumulated within {@code k} steps.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 */
	public ModelCheckerResult computeCumulativeRewards(MDP mdp, MDPRewards mdpRewards, int k, boolean min)
			throws PrismException {
		ModelCheckerResult res = null;
		int i, n, iters;
		long timer;
		double soln[], soln2[], tmpsoln[];

		// Start expected cumulative reward
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting expected cumulative reward (" + (min ? "min" : "max") + ")...");

		// Store num states
		n = mdp.getNumStates();

		// Create/initialise solution vector(s)
		soln = new double[n];
		soln2 = new double[n];
		for (i = 0; i < n; i++)
			soln[i] = soln2[i] = 0.0;

		// Start iterations
		iters = 0;
		while (iters < k) {
			iters++;
			// Matrix-vector multiply and min/max ops
			mdp.mvMultRewMinMax(soln, mdpRewards, min, soln2, null, false, null);
			// Swap vectors for next iter
			tmpsoln = soln;
			soln = soln2;
			soln2 = tmpsoln;
		}

		// Finished value iteration
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Expected cumulative reward (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;

		return res;
	}

	/**
	 * Compute expected instantaneous reward, i.e. compute the min/max expected
	 * reward of the states after {@code k} steps.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param k
	 *            the number of steps
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 */
	public ModelCheckerResult computeInstantaneousRewards(MDP mdp, MDPRewards mdpRewards, final int k, boolean min) {
		ModelCheckerResult res = null;
		int i, n, iters;
		double soln[], soln2[], tmpsoln[];
		long timer;

		// Store num states
		n = mdp.getNumStates();

		// Start backwards transient computation
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting backwards instantaneous rewards computation...");

		// Create solution vector(s)
		soln = new double[n];
		soln2 = new double[n];

		// Initialise solution vectors.
		for (i = 0; i < n; i++)
			soln[i] = mdpRewards.getStateReward(i);

		// Start iterations
		for (iters = 0; iters < k; iters++) {
			// Matrix-vector multiply
			mdp.mvMultMinMax(soln, min, soln2, null, false, null);
			// Swap vectors for next iter
			tmpsoln = soln;
			soln = soln2;
			soln2 = tmpsoln;
		}

		// Finished backwards transient computation
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Backwards transient instantaneous rewards computation");
		mainLog.println(" took " + iters + " iters and " + timer / 1000.0 + " seconds.");

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.lastSoln = soln2;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		res.timePre = 0.0;
		return res;
	}

	/**
	 * Compute expected reachability rewards.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 */
	public ModelCheckerResult computeReachRewards(MDP mdp, MDPRewards mdpRewards, BitSet target, boolean min)
			throws PrismException {
		return computeReachRewards(mdp, mdpRewards, target, min, null, null);
	}

	/**
	 * Compute expected reachability rewards. i.e. compute the min/max reward
	 * accumulated to reach a state in {@code target}.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param target
	 *            Target states
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (may be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values). Also,
	 *            'known' values cannot be passed for some solution methods,
	 *            e.g. policy iteration.
	 */
	public ModelCheckerResult computeReachRewards(MDP mdp, MDPRewards mdpRewards, BitSet target, boolean min,
			double init[], BitSet known) throws PrismException {
		ModelCheckerResult res = null;
		BitSet inf;
		int n, numTarget, numInf;
		long timer, timerProb1;
		int strat[] = null;
		// Local copy of setting
		MDPSolnMethod mdpSolnMethod = this.mdpSolnMethod;

		// Switch to a supported method, if necessary
		if (!(mdpSolnMethod == MDPSolnMethod.VALUE_ITERATION || mdpSolnMethod == MDPSolnMethod.GAUSS_SEIDEL
				|| mdpSolnMethod == MDPSolnMethod.POLICY_ITERATION)) {
			mdpSolnMethod = MDPSolnMethod.GAUSS_SEIDEL;
			mainLog.printWarning("Switching to MDP solution method \"" + mdpSolnMethod.fullName() + "\"");
		}

		// Check for some unsupported combinations
		if (mdpSolnMethod == MDPSolnMethod.POLICY_ITERATION) {
			if (known != null) {
				throw new PrismException("Policy iteration methods cannot be passed 'known' values for some states");
			}
		}

		// Start expected reachability
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting expected reachability (" + (min ? "min" : "max") + ")...");

		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		mdp.checkForDeadlocks(target);

		// Store num states
		n = mdp.getNumStates();
		// Optimise by enlarging target set (if more info is available)
		if (init != null && known != null && !known.isEmpty()) {
			BitSet targetNew = (BitSet) target.clone();
			for (int i : new IterableBitSet(known)) {
				if (init[i] == 1.0) {
					targetNew.set(i);
				}
			}
			target = targetNew;
		}

		// If required, export info about target states
		if (getExportTarget()) {
			BitSet bsInit = new BitSet(n);
			for (int i = 0; i < n; i++) {
				bsInit.set(i, mdp.isInitialState(i));
			}
			List<BitSet> labels = Arrays.asList(bsInit, target);
			List<String> labelNames = Arrays.asList("init", "target");
			mainLog.println("\nExporting target states info to file \"" + getExportTargetFilename() + "\"...");
			exportLabels(mdp, labels, labelNames, Prism.EXPORT_PLAIN, new PrismFileLog(getExportTargetFilename()));
		}

		// If required, create/initialise strategy storage
		// Set choices to -1, denoting unknown
		// (except for target states, which are -2, denoting arbitrary)
		if (genStrat || exportAdv || mdpSolnMethod == MDPSolnMethod.POLICY_ITERATION) {
			strat = new int[n];
			for (int i = 0; i < n; i++) {
				strat[i] = target.get(i) ? -2 : -1;
			}
		}

		// Precomputation (not optional)
		timerProb1 = System.currentTimeMillis();
		inf = prob1(mdp, null, target, !min, strat);
		inf.flip(0, n);
		timerProb1 = System.currentTimeMillis() - timerProb1;

		// Print results of precomputation
		numTarget = target.cardinality();
		numInf = inf.cardinality();
		mainLog.println("target=" + numTarget + ", inf=" + numInf + ", rest=" + (n - (numTarget + numInf)));

		// If required, generate strategy for "inf" states.
		if (genStrat || exportAdv || mdpSolnMethod == MDPSolnMethod.POLICY_ITERATION) {
			if (min) {
				// If min reward is infinite, all choices give infinity
				// So the choice can be arbitrary, denoted by -2;
				for (int i = inf.nextSetBit(0); i >= 0; i = inf.nextSetBit(i + 1)) {
					strat[i] = -2;
				}
			} else {
				// If max reward is infinite, there is at least one choice
				// giving infinity.
				// So we pick, for all "inf" states, the first choice for which
				// some transitions stays in "inf".
				for (int i = inf.nextSetBit(0); i >= 0; i = inf.nextSetBit(i + 1)) {
					int numChoices = mdp.getNumChoices(i);
					for (int k = 0; k < numChoices; k++) {
						if (mdp.someSuccessorsInSet(i, k, inf)) {
							strat[i] = k;
							continue;
						}
					}
				}
			}
		}

		// Compute rewards
		switch (mdpSolnMethod) {
		case VALUE_ITERATION:
			res = computeReachRewardsValIter(mdp, mdpRewards, target, inf, min, init, known, strat);
			break;
		case GAUSS_SEIDEL:
			res = computeReachRewardsGaussSeidel(mdp, mdpRewards, target, inf, min, init, known, strat);
			break;
		case POLICY_ITERATION:
			res = computeReachRewardsPolIter(mdp, mdpRewards, target, inf, min, strat);
			break;
		default:
			throw new PrismException("Unknown MDP solution method " + mdpSolnMethod.fullName());
		}

		// Store strategy
		if (genStrat) {
			res.strat = new MDStrategyArray(mdp, strat);
		}
		// Export adversary
		if (exportAdv) {
			// Prune strategy
			restrictStrategyToReachableStates(mdp, strat);
			// Export
			PrismLog out = new PrismFileLog(exportAdvFilename);
			new DTMCFromMDPMemorylessAdversary(mdp, strat).exportToPrismExplicitTra(out);
			out.close();
		}

		// Finished expected reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Expected reachability took " + timer / 1000.0 + " seconds.");

		// Update time taken
		res.timeTaken = timer / 1000.0;
		res.timePre = timerProb1 / 1000.0;

		return res;
	}

	/**
	 * Compute expected reachability rewards using value iteration. Optionally,
	 * store optimal (memoryless) strategy info.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param target
	 *            Target states
	 * @param inf
	 *            States for which reward is infinite
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (will be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null) Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values.
	 */
	protected ModelCheckerResult computeReachRewardsValIter(MDP mdp, MDPRewards mdpRewards, BitSet target, BitSet inf,
			boolean min, double init[], BitSet known, int strat[]) throws PrismException {
		ModelCheckerResult res;
		BitSet unknown;
		int i, n, iters;
		double soln[], soln2[], tmpsoln[];
		boolean done;
		long timer;

		// Start value iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting value iteration (" + (min ? "min" : "max") + ")...");

		// Store num states
		n = mdp.getNumStates();

		// Create solution vector(s)
		soln = new double[n];
		soln2 = (init == null) ? new double[n] : init;

		// Initialise solution vectors. Use (where available) the following in
		// order of preference:
		// (1) exact answer, if already known; (2) 0.0/infinity if in
		// target/inf; (3) passed in initial value; (4) 0.0
		if (init != null) {
			if (known != null) {
				for (i = 0; i < n; i++)
					soln[i] = soln2[i] = known.get(i) ? init[i]
							: target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : init[i];
			} else {
				for (i = 0; i < n; i++)
					soln[i] = soln2[i] = target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : init[i];
			}
		} else {
			for (i = 0; i < n; i++)
				soln[i] = soln2[i] = target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : 0.0;
		}

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		unknown.andNot(inf);
		if (known != null)
			unknown.andNot(known);

		// Start iterations
		iters = 0;
		done = false;
		while (!done && iters < maxIters) {
			// mainLog.println(soln);
			iters++;
			// Matrix-vector multiply and min/max ops
			mdp.mvMultRewMinMax(soln, mdpRewards, min, soln2, unknown, false, strat);
			// Check termination
			done = PrismUtils.doublesAreClose(soln, soln2, termCritParam, termCrit == TermCrit.ABSOLUTE);
			// Swap vectors for next iter
			tmpsoln = soln;
			soln = soln2;
			soln2 = tmpsoln;
		}

		// Finished value iteration
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Value iteration (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// Non-convergence is an error (usually)
		if (!done && errorOnNonConverge) {
			String msg = "Iterative method did not converge within " + iters + " iterations.";
			msg += "\nConsider using a different numerical method or increasing the maximum number of iterations";
			throw new PrismException(msg);
		}

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Compute expected reachability rewards using Gauss-Seidel (including
	 * Jacobi-style updates). Optionally, store optimal (memoryless) strategy
	 * info.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param target
	 *            Target states
	 * @param inf
	 *            States for which reward is infinite
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 * @param init
	 *            Optionally, an initial solution vector (will be overwritten)
	 * @param known
	 *            Optionally, a set of states for which the exact answer is
	 *            known
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null) Note: if 'known' is specified (i.e. is non-null, 'init'
	 *            must also be given and is used for the exact values.
	 */
	protected ModelCheckerResult computeReachRewardsGaussSeidel(MDP mdp, MDPRewards mdpRewards, BitSet target,
			BitSet inf, boolean min, double init[], BitSet known, int strat[]) throws PrismException {
		ModelCheckerResult res;
		BitSet unknown;
		int i, n, iters;
		double soln[], maxDiff;
		boolean done;
		long timer;

		// Start value iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting Gauss-Seidel (" + (min ? "min" : "max") + ")...");

		// Store num states
		n = mdp.getNumStates();

		// Create solution vector(s)
		soln = (init == null) ? new double[n] : init;

		// Initialise solution vector. Use (where available) the following in
		// order of preference:
		// (1) exact answer, if already known; (2) 0.0/infinity if in
		// target/inf; (3) passed in initial value; (4) 0.0
		if (init != null) {
			if (known != null) {
				for (i = 0; i < n; i++)
					soln[i] = known.get(i) ? init[i]
							: target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : init[i];
			} else {
				for (i = 0; i < n; i++)
					soln[i] = target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : init[i];
			}
		} else {
			for (i = 0; i < n; i++)
				soln[i] = target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : 0.0;
		}

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		unknown.andNot(inf);
		if (known != null)
			unknown.andNot(known);

		// Start iterations
		iters = 0;
		done = false;
		while (!done && iters < maxIters) {
			// mainLog.println(soln);
			iters++;
			// Matrix-vector multiply and min/max ops
			maxDiff = mdp.mvMultRewGSMinMax(soln, mdpRewards, min, unknown, false, termCrit == TermCrit.ABSOLUTE,
					strat);
			// Check termination
			done = maxDiff < termCritParam;
		}

		// Finished Gauss-Seidel
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Gauss-Seidel (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// Non-convergence is an error (usually)
		if (!done && errorOnNonConverge) {
			String msg = "Iterative method did not converge within " + iters + " iterations.";
			msg += "\nConsider using a different numerical method or increasing the maximum number of iterations";
			throw new PrismException(msg);
		}

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Compute expected reachability rewards using policy iteration. The array
	 * {@code strat} is used both to pass in the initial strategy for policy
	 * iteration, and as storage for the resulting optimal strategy (if needed).
	 * Passing in an initial strategy is required when some states have infinite
	 * reward, to avoid the possibility of policy iteration getting stuck on an
	 * infinite-value strategy.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param target
	 *            Target states
	 * @param inf
	 *            States for which reward is infinite
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 * @param strat
	 *            Storage for (memoryless) strategy choice indices (ignored if
	 *            null)
	 */
	protected ModelCheckerResult computeReachRewardsPolIter(MDP mdp, MDPRewards mdpRewards, BitSet target, BitSet inf,
			boolean min, int strat[]) throws PrismException {
		ModelCheckerResult res;
		int i, n, iters, totalIters;
		double soln[], soln2[];
		boolean done;
		long timer;
		DTMCModelChecker mcDTMC;
		DTMC dtmc;
		MCRewards mcRewards;

		// Re-use solution to solve each new policy (strategy)?
		boolean reUseSoln = true;

		// Start policy iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting policy iteration (" + (min ? "min" : "max") + ")...");

		// Create a DTMC model checker (for solving policies)
		mcDTMC = new DTMCModelChecker(this);
		mcDTMC.inheritSettings(this);
		mcDTMC.setLog(new PrismDevNullLog());

		// Store num states
		n = mdp.getNumStates();

		// Create solution vector(s)
		soln = new double[n];
		soln2 = new double[n];

		// Initialise solution vectors.
		for (i = 0; i < n; i++)
			soln[i] = soln2[i] = target.get(i) ? 0.0 : inf.get(i) ? Double.POSITIVE_INFINITY : 0.0;

		// If not passed in, create new storage for strategy and initialise
		// Initial strategy just picks first choice (0) everywhere
		if (strat == null) {
			strat = new int[n];
			for (i = 0; i < n; i++)
				strat[i] = 0;
		}

		// Start iterations
		iters = totalIters = 0;
		done = false;
		while (!done && iters < maxIters) {
			iters++;
			// Solve induced DTMC for strategy
			dtmc = new DTMCFromMDPMemorylessAdversary(mdp, strat);
			mcRewards = new MCRewardsFromMDPRewards(mdpRewards, strat);
			res = mcDTMC.computeReachRewardsValIter(dtmc, mcRewards, target, inf, reUseSoln ? soln : null, null);
			soln = res.soln;
			totalIters += res.numIters;
			// Check if optimal, improve non-optimal choices
			mdp.mvMultRewMinMax(soln, mdpRewards, min, soln2, null, false, null);
			done = true;
			for (i = 0; i < n; i++) {
				// Don't look at target/inf states - we may not have strategy
				// info for them,
				// so they might appear non-optimal
				if (target.get(i) || inf.get(i))
					continue;
				if (!PrismUtils.doublesAreClose(soln[i], soln2[i], termCritParam, termCrit == TermCrit.ABSOLUTE)) {
					done = false;
					List<Integer> opt = mdp.mvMultRewMinMaxSingleChoices(i, soln, mdpRewards, min, soln2[i]);
					// Only update strategy if strictly better
					if (!opt.contains(strat[i]))
						strat[i] = opt.get(0);
				}
			}
		}

		// Finished policy iteration
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Policy iteration");
		mainLog.println(" took " + iters + " cycles (" + totalIters + " iterations in total) and " + timer / 1000.0
				+ " seconds.");

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.numIters = totalIters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Construct strategy information for min/max expected reachability. (More
	 * precisely, list of indices of choices resulting in min/max.) (Note:
	 * indices are guaranteed to be sorted in ascending order.)
	 * 
	 * @param mdp
	 *            The MDP
	 * @param mdpRewards
	 *            The rewards
	 * @param state
	 *            The state to generate strategy info for
	 * @param target
	 *            The set of target states to reach
	 * @param min
	 *            Min or max rewards (true=min, false=max)
	 * @param lastSoln
	 *            Vector of values from which to recompute in one iteration
	 */
	public List<Integer> expReachStrategy(MDP mdp, MDPRewards mdpRewards, int state, BitSet target, boolean min,
			double lastSoln[]) throws PrismException {
		double val = mdp.mvMultRewMinMaxSingle(state, lastSoln, mdpRewards, min, null);
		return mdp.mvMultRewMinMaxSingleChoices(state, lastSoln, mdpRewards, min, val);
	}

	/**
	 * Restrict a (memoryless) strategy for an MDP, stored as an integer array
	 * of choice indices, to the states of the MDP that are reachable under that
	 * strategy.
	 * 
	 * @param mdp
	 *            The MDP
	 * @param strat
	 *            The strategy
	 */
	public BitSet restrictStrategyToReachableStates(MDP mdp, int strat[]) {
		BitSet restrict = new BitSet();
		BitSet explore = new BitSet();
		// Get initial states
		for (int is : mdp.getInitialStates()) {
			restrict.set(is);
			explore.set(is);
		}
		// Compute reachable states (store in 'restrict')
		boolean foundMore = true;
		while (foundMore) {
			foundMore = false;
			for (int s = explore.nextSetBit(0); s >= 0; s = explore.nextSetBit(s + 1)) {
				explore.set(s, false);
				if (strat[s] >= 0) {
					Iterator<Map.Entry<Integer, Double>> iter = mdp.getTransitionsIterator(s, strat[s]);
					while (iter.hasNext()) {
						Map.Entry<Integer, Double> e = iter.next();
						int dest = e.getKey();
						if (!restrict.get(dest)) {
							foundMore = true;
							restrict.set(dest);
							explore.set(dest);
						}
					}
				}
			}
		}
		// Set strategy choice for non-reachable state to -1
		int n = mdp.getNumStates();
		for (int s = restrict.nextClearBit(0); s < n; s = restrict.nextClearBit(s + 1)) {
			strat[s] = -3;
		}
		return restrict;
	}

	public BitSet restrictStrategyToReachableStates(MDP mdp, MDStrategy strat) {
		int n, intStrat[];
		BitSet res;

		n = strat.getNumStates();
		intStrat = new int[n];

		for (int i = 0; i < n; i++) {
			intStrat[i] = strat.getChoiceIndex(i);
		}
		res = restrictStrategyToReachableStates(mdp, intStrat);
		strat = new MDStrategyArray(mdp, intStrat);
		return res;
	}

	/**
	 * Simple test program.
	 */
	public static void main(String args[]) {
		MDPModelChecker mc;
		MDPSimple mdp;
		ModelCheckerResult res;
		BitSet init, target;
		Map<String, BitSet> labels;
		boolean min = true;
		try {
			mc = new MDPModelChecker(null);
			mdp = new MDPSimple();
			mdp.buildFromPrismExplicit(args[0]);
			mdp.addInitialState(0);
			// System.out.println(mdp);
			labels = StateModelChecker.loadLabelsFile(args[1]);
			// System.out.println(labels);
			init = labels.get("init");
			target = labels.get(args[2]);
			if (target == null)
				throw new PrismException("Unknown label \"" + args[2] + "\"");
			for (int i = 3; i < args.length; i++) {
				if (args[i].equals("-min"))
					min = true;
				else if (args[i].equals("-max"))
					min = false;
				else if (args[i].equals("-nopre"))
					mc.setPrecomp(false);
			}
			res = mc.computeReachProbs(mdp, target, min);
			System.out.println(res.soln[init.nextSetBit(0)]);
		} catch (PrismException e) {
			System.out.println(e);
		}
	}
}
