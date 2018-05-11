/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.scheduler

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.Lists
import org.gradle.api.BuildCancelledException
import org.gradle.api.specs.Spec
import org.gradle.execution.MultipleBuildFailures
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.graph.DirectedGraph
import org.gradle.internal.graph.DirectedGraphRenderer
import org.gradle.internal.graph.GraphNodeRenderer
import org.gradle.internal.logging.text.StyledTextOutput

import static org.gradle.internal.scheduler.EdgeType.DEPENDENCY_OF
import static org.gradle.internal.scheduler.EdgeType.FINALIZED_BY
import static org.gradle.internal.scheduler.EdgeType.MUST_COMPLETE_BEFORE
import static org.gradle.internal.scheduler.EdgeType.SHOULD_COMPLETE_BEFORE

abstract class AbstractSchedulerTest extends AbstractSchedulingTest {
    static final TASK_NODE_COMPARATOR = new Comparator<? super Node>() {
        @Override
        int compare(def a, def b) {
            def result = a.project <=> b.project
            if (result != 0) {
                return result
            }
            return a.name <=> b.name
        }
    }

    def cancellationHandler = Mock(BuildCancellationToken)
    def graph = new Graph()
    def nodeExecutor = new TaskNodeExecutor()
    List<Node> nodesToExecute = []
    Predicate<? super Node> filter = Predicates.alwaysTrue()
    boolean continueOnFailure

    GraphExecutionResult results

    def cycleReporter = new CycleReporter() {
        @Override
        String reportCycle(Collection<Node> cycle) {
            def sortedCycle = Lists.newArrayList(cycle)
            Collections.sort(sortedCycle, TASK_NODE_COMPARATOR)
            DirectedGraphRenderer<Node> graphRenderer = new DirectedGraphRenderer<Node>(new GraphNodeRenderer<Node>() {
                @Override
                void renderTo(Node node, StyledTextOutput output) {
                    output.withStyle(StyledTextOutput.Style.Identifier).text(node)
                }
            }, new DirectedGraph<Node, Object>() {
                @Override
                void getNodeValues(Node node, Collection<? super Object> values, Collection<? super Node> connectedNodes) {
                    for (Node dependency : sortedCycle) {
                        for (Edge incoming : graph.getIncomingEdges(node)) {
                            if ((incoming.getType() == DEPENDENCY_OF || incoming.getType() == MUST_COMPLETE_BEFORE)
                                && incoming.getSource() == dependency) {
                                connectedNodes.add(dependency)
                            }
                        }
                        for (Edge outgoing : graph.getOutgoingEdges(node)) {
                            if (outgoing.getType() == FINALIZED_BY && outgoing.getTarget() == dependency) {
                                connectedNodes.add(dependency)
                            }
                        }
                    }
                }
            })
            StringWriter writer = new StringWriter()
            def firstNode = sortedCycle.get(0)
            graphRenderer.renderTo(firstNode, writer)
            return writer.toString()
        }
    }

    abstract Scheduler getScheduler()

    def "stops returning tasks when build is cancelled"() {
        2 * cancellationHandler.isCancellationRequested() >>> [false, true]
        def a = task("a")
        def b = task("b")

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]

        when:
        rethrowFailures()

        then:
        BuildCancelledException e = thrown()
        e.message == 'Build cancelled.'
    }

    protected void executeGraph(List<Node> entryNodes) {
        def scheduler = getScheduler()
        try {
            results = scheduler.execute(graph, entryNodes, continueOnFailure, filter, nodeExecutor)
        } finally {
            scheduler.close()
        }
    }

    @Override
    protected void addToGraph(List tasks) {
        nodesToExecute.addAll(tasks)
    }

    @Override
    protected void determineExecutionPlan() {
        executeGraph(nodesToExecute.empty ? graph.allNodes : nodesToExecute)
    }

    @Override
    protected void executes(Object... expectedNodes) {
        assert results.executedNodes == (expectedNodes as List)
    }

    @Override
    protected Set getAllTasks() {
        return results.liveNodes as Set
    }

    @Override
    protected List getExecutedTasks() {
        return results.executedNodes
    }

    @Override
    protected createTask(String name) {
        task(name)
    }

    @Override
    protected TaskNode task(Map options, String name) {
        String project = options.project ?: ""
        Exception failure = options.failure
        def task = new TaskNode(project, name, failure)
        graph.addNode(task)
        relationships(options, task)
        return task
    }

    @Override
    protected void relationships(Map options, def task) {
        options.dependsOn?.each { TaskNode dependency ->
            graph.addEdge(new Edge(dependency, DEPENDENCY_OF, task as TaskNode))
        }
        options.mustRunAfter?.each { TaskNode predecessor ->
            graph.addEdge(new Edge(predecessor, MUST_COMPLETE_BEFORE, task as TaskNode))
        }
        options.shouldRunAfter?.each { TaskNode predecessor ->
            graph.addEdge(new Edge(predecessor, SHOULD_COMPLETE_BEFORE, task as TaskNode))
        }
        options.finalizedBy?.each { TaskNode finalizer ->
            graph.addEdge(new Edge(task as TaskNode, FINALIZED_BY, finalizer))
        }
    }

    @Override
    protected void filtered(Object... expectedNodes) {
        assert results.filteredNodes == (expectedNodes as List)
    }

    @Override
    protected filteredTask(String name) {
        return task(fails: true, name)
    }

    @Override
    protected void useFilter(Spec filter) {
        this.filter = { node -> filter.isSatisfiedBy(node) }
    }

    @Override
    protected void rethrowFailures() {
        switch (results.failures.size()) {
            case 0:
                break
            case 1:
                throw results.failures[0]
            default:
                throw new MultipleBuildFailures(results.failures)
        }
    }

    @Override
    protected void continueOnFailure() {
        continueOnFailure = true
    }
}