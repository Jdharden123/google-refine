package com.google.refine.freebase.protograph.transpose;

import java.util.LinkedList;
import java.util.List;

import com.google.refine.browsing.FilteredRows;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.expr.ExpressionUtils;
import com.google.refine.freebase.protograph.AnonymousNode;
import com.google.refine.freebase.protograph.CellNode;
import com.google.refine.freebase.protograph.CellTopicNode;
import com.google.refine.freebase.protograph.FreebaseTopicNode;
import com.google.refine.freebase.protograph.Link;
import com.google.refine.freebase.protograph.Node;
import com.google.refine.freebase.protograph.NodeWithLinks;
import com.google.refine.freebase.protograph.Protograph;
import com.google.refine.freebase.protograph.ValueNode;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.model.Recon.Judgment;

public class Transposer {
    static public void transpose(
        Project                 project,
        FilteredRows            filteredRows,
        Protograph              protograph,
        Node                    rootNode,
        TransposedNodeFactory   nodeFactory
    ) {
        transpose(project, filteredRows, protograph, rootNode, nodeFactory, 20);
    }
    
    static public void transpose(
        Project                 project,
        FilteredRows            filteredRows,
        Protograph              protograph,
        Node                    rootNode,
        TransposedNodeFactory   nodeFactory,
        int                     limit
    ) {
        Context rootContext = new Context(rootNode, null, null, limit);
        
        filteredRows.accept(project, new RowVisitor() {
            Context                 rootContext;
            Protograph              protograph;
            Node                    rootNode;
            TransposedNodeFactory   nodeFactory;
            
            @Override
            public boolean visit(Project project, int rowIndex, Row row) {
                if (rootContext.limit <= 0 || rootContext.count < rootContext.limit) {
                    descend(project, protograph, nodeFactory, rowIndex, row, rootNode, rootContext);
                }
                
                if (rootContext.limit > 0 && rootContext.count > rootContext.limit) {
                    return true;
                }
                return false;
            }
            
            @Override
            public void start(Project project) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void end(Project project) {
                // TODO Auto-generated method stub
                
            }
            
            public RowVisitor init(
                Context                 rootContext,
                Protograph              protograph,
                Node                    rootNode,
                TransposedNodeFactory   nodeFactory
            ) {
                this.rootContext = rootContext;
                this.protograph = protograph;
                this.rootNode = rootNode;
                this.nodeFactory = nodeFactory;
                
                return this;
            }
        }.init(rootContext, protograph, rootNode, nodeFactory));
    }
    
    static protected void descend(
        Project project,
        Protograph protograph, 
        TransposedNodeFactory nodeFactory,
        int rowIndex, 
        Row row,
        Node node,
        Context context
    ) {
        List<TransposedNode> tnodes = new LinkedList<TransposedNode>();
        
        TransposedNode parentNode = context.parent == null ? null : context.parent.transposedNode;
        Link link = context.parent == null ? null : context.link;
        
        if (node instanceof CellNode) {
            CellNode node2 = (CellNode) node;
            for (String columnName : node2.columnNames) {
                Column column = project.columnModel.getColumnByName(columnName);
                if (column != null) {
                    int cellIndex = column.getCellIndex();
                    
                    Cell cell = row.getCell(cellIndex);
                    if (cell != null && ExpressionUtils.isNonBlankData(cell.value)) {
                        if (node2 instanceof CellTopicNode &&
                            (cell.recon == null || cell.recon.judgment == Judgment.None)) {
                                return;
                        }
                        
                        context.count++;
                        if (context.limit > 0 && context.count > context.limit) {
                            return;
                        }
                        
                        tnodes.add(nodeFactory.transposeCellNode(
                            parentNode,
                            link,
                            node2, 
                            rowIndex,
                            cellIndex,
                            cell
                        ));
                    }
                }
            }
        } else {
            if (node instanceof AnonymousNode) {
                tnodes.add(nodeFactory.transposeAnonymousNode(
                    parentNode,
                    link,
                    (AnonymousNode) node,
                    rowIndex
                ));
            } else if (node instanceof FreebaseTopicNode) {
                tnodes.add(nodeFactory.transposeTopicNode(
                    parentNode,
                    link,
                    (FreebaseTopicNode) node,
                    rowIndex
                ));
            } else if (node instanceof ValueNode) {
                tnodes.add(nodeFactory.transposeValueNode(
                    parentNode,
                    link,
                    (ValueNode) node,
                    rowIndex
                ));
            }
        }
        
        if (node instanceof NodeWithLinks) {
            NodeWithLinks node2 = (NodeWithLinks) node;
            int linkCount = node2.getLinkCount();
            
            for (int i = 0; i < linkCount; i++) {
                Link link2 = node2.getLink(i);
                if (link2.condition == null || link2.condition.test(project, rowIndex, row)) {
                    for (TransposedNode tnode : tnodes) {
                        context.transposedNode = tnode;
                        context.nullifySubContextNodes();

                        descend(
                            project, 
                            protograph, 
                            nodeFactory,
                            rowIndex,
                            row, 
                            link2.getTarget(), 
                            context.subContexts.get(i)
                        );
                    }
                }
            }
        }
    }
    
    static class Context {
        TransposedNode    transposedNode;
        List<Context>     subContexts;
        Context           parent;
        Link              link;
        int               count;
        int               limit;
        
        Context(Node node, Context parent, Link link, int limit) {
            this.parent = parent;
            this.link = link;
            this.limit = limit;
            
            if (node instanceof NodeWithLinks) {
                NodeWithLinks node2 = (NodeWithLinks) node;
                
                int subContextCount = node2.getLinkCount();
                
                subContexts = new LinkedList<Context>();
                for (int i = 0; i < subContextCount; i++) {
                    Link link2 = node2.getLink(i);
                    subContexts.add(
                        new Context(link2.getTarget(), this, link2, -1));
                }
            }
        }
        
        public void nullifySubContextNodes() {
            if (subContexts != null) {
                for (Context context : subContexts) {
                    context.transposedNode = null;
                    context.nullifySubContextNodes();
                }
            }
        }
    }
}