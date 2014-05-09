package org.nerdpower.tabula;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings("serial")
public class Ruling extends Line2D.Float {
    
    private static int PERPENDICULAR_PIXEL_EXPAND_AMOUNT = 2;
    private static int COLINEAR_OR_PARALLEL_PIXEL_EXPAND_AMOUNT = 1;
    private static float ORIENTATION_CHECK_THRESHOLD = 0.00001f;
    private enum SOType { VERTICAL, HRIGHT, HLEFT };

    public Ruling(float top, float left, float width, float height) {
        super(left, top, left+width, top+height);
    }
    
    public Ruling(Point2D p1, Point2D p2) {
        super(p1, p2);
    }

    public boolean vertical() {
        float diff = Math.abs(this.x1 - this.x2);
//        if (diff > 0 && diff < 0.5)
//          System.out.println("Vertical: " + this.x1 + ", " + this.x2);
        return diff < ORIENTATION_CHECK_THRESHOLD;
    }
    
    public boolean horizontal() {
        float diff = Math.abs(this.y1 - this.y2);
//        if (diff > 0 && diff < 0.5)
//          System.out.println("Horizontal: " + this.y1 + ", " + this.y2);
        return diff < ORIENTATION_CHECK_THRESHOLD;
    }
    
    public boolean oblique() {
        return !(this.vertical() || this.horizontal());
    }
    
    // attributes that make sense only for non-oblique lines
    // these are used to have a single collapse method (in page, currently)
    
    public float getPosition() {
        if (this.oblique()) {
            throw new UnsupportedOperationException();
        }
        return this.vertical() ? this.getLeft() : this.getTop();
    }
    
    public void setPosition(float v) {
        if (this.oblique()) {
            throw new UnsupportedOperationException();
        }
        if (this.vertical()) {
            this.setLeft(v);
            this.setRight(v);
        }
        else {
            this.setTop(v);
            this.setBottom(v);
        }
    }
    
    public float getStart() {
        if (this.oblique()) {
            throw new UnsupportedOperationException();
        }
        return this.vertical() ? this.getTop() : this.getLeft();
    }
    
    public void setStart(float v) {
        if (this.oblique()) {
            throw new UnsupportedOperationException();
        }
        if (this.vertical()) {
            this.setTop(v);
        }
        else {
            this.setLeft(v);
        }
    }
    
    public float getEnd() {
        if (this.oblique()) {
            throw new UnsupportedOperationException();
        }
        return this.vertical() ? this.getBottom() : this.getRight();
    }
    
    public void setEnd(float v) {
        if (this.oblique()) {
            throw new UnsupportedOperationException();
        }
        if (this.vertical()) {
            this.setBottom(v);
        }
        else {
            this.setRight(v);
        }
    }
    
    // -----
        
    public boolean perpendicularTo(Ruling other) {
        return this.vertical() == other.horizontal();
    }
    
    public boolean colinear(Point2D point) {
        return point.getX() >= this.x1
                && point.getX() <= this.x2
                && point.getY() >= this.y1
                && point.getY() <= this.y2;
    }
    
    // if the lines we're comparing are colinear or parallel, we expand them by a only 1 pixel,
    // because the expansions are additive
    // (e.g. two vertical lines, at x = 100, with one having y2 of 98 and the other having y1 of 102 would
    // erroneously be said to nearlyIntersect if they were each expanded by 2 (since they'd both terminate at 100).
    // The COLINEAR_OR_PARALLEL_PIXEL_EXPAND_AMOUNT is only 1 so the total expansion is 2.
    // A total expansion amount of 2 is empirically verified to work sometimes. It's not a magic number from any
    // source other than a little bit of experience.)
    public boolean nearlyIntersects(Ruling another) {
        if (this.intersectsLine(another)) {
            return true;
        }
        
        boolean rv = false;
        
        if (this.perpendicularTo(another)) {
            rv = this.expand(PERPENDICULAR_PIXEL_EXPAND_AMOUNT).intersectsLine(another);
        }
        else {
            rv = this.expand(COLINEAR_OR_PARALLEL_PIXEL_EXPAND_AMOUNT)
                    .intersectsLine(another.expand(COLINEAR_OR_PARALLEL_PIXEL_EXPAND_AMOUNT));
        }
        
        return rv;
    }
    
    public double length() {
        return Math.sqrt(Math.pow(this.x1 - this.x2, 2) + Math.pow(this.y1 - this.y2, 2));
    }
    
    public Ruling intersect(Rectangle2D clip) {
        Line2D.Float clipee = (Line2D.Float) this.clone();
        boolean clipped = new CohenSutherlandClipping(clip).clip(clipee);

        if (clipped) {
            return new Ruling(clipee.getP1(), clipee.getP2());
        }
        else {
            return this;
        }
    }
    
    public Ruling expand(float amount) {
        Ruling r = (Ruling) this.clone();
        r.setStart(this.getStart() - amount);
        r.setEnd(this.getEnd() + amount);
        return r;
    }
    
    public Point2D intersectionPoint(Ruling other) {
        Ruling this_l = this.expand(PERPENDICULAR_PIXEL_EXPAND_AMOUNT);
        Ruling other_l = other.expand(PERPENDICULAR_PIXEL_EXPAND_AMOUNT);
        Ruling horizontal, vertical;
        
        if (!this_l.intersectsLine(other_l)) {
            return null;
        }
        
        if (this_l.horizontal() && other_l.vertical()) {
            horizontal = this_l; vertical = other_l;
        }
        else if (this_l.vertical() && other_l.horizontal()) {
            vertical = this_l; horizontal = other_l;
        }
        else {
//            System.out.println("this: " + this_l.toString());
//            System.out.println("other: " + other_l.toString());
//            System.out.println("Is other vertical? " + other_l.vertical());
            
            throw new IllegalArgumentException("lines must be orthogonal, vertical and horizontal");
        }
        return new Point2D.Float(vertical.getLeft(), horizontal.getTop());        
    }
    
    public float getTop() {
        return (float) this.y1;
    }
    
    public void setTop(float v) {
        setLine(this.getLeft(), v, this.getRight(), this.getBottom());
    }
    
    public float getLeft() {
        return (float) this.x1;
    }
    
    public void setLeft(float v) {
        setLine(v, this.getTop(), this.getRight(), this.getBottom());
    }
    
    public float getBottom() {
        return (float) this.y2;
    }
    
    public void setBottom(float v) {
        setLine(this.getLeft(), this.getTop(), this.getRight(), v);
    }

    public float getRight() {
        return (float) this.x2;
    }
    
    public void setRight(float v) {
        setLine(this.getLeft(), this.getTop(), v, this.getBottom());
    }
    
    public float getWidth() {
        return this.getRight() - this.getLeft();
    }
    
    public float getHeight() {
        return this.getBottom() - this.getTop();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb);
        String rv = formatter.format("org.nerdpower.tabula.Ruling[x1=%f y1=%f x2=%f y2=%f]", this.x1, this.y1, this.x2, this.y2).toString();
        formatter.close();
        return rv;
    }
    
    public static List<Ruling> cropRulingsToArea(List<Ruling> rulings, Rectangle2D area) {
        ArrayList<Ruling> rv = new ArrayList<Ruling>();
        for (Ruling r : rulings) {
            if (r.intersects(area)) {
                rv.add(r.intersect(area));
            }
        }
        return rv;
    }
    
    // log(n) implementation of find_intersections
    // based on http://people.csail.mit.edu/indyk/6.838-old/handouts/lec2.pdf
    public static TreeMap<Point2D, Ruling[]> findIntersections(List<Ruling> horizontals, List<Ruling> verticals) {
        
        class SortObject {
            protected SOType type;
            protected float position;
            protected Ruling ruling;
            
            public SortObject(SOType type, float position, Ruling ruling) {
                this.type = type;
                this.position = position;
                this.ruling = ruling;
            }
        }
        
        List<SortObject> sos = new ArrayList<SortObject>();
        
        TreeMap<Ruling, Boolean> tree = new TreeMap<Ruling, Boolean>(new Comparator<Ruling>() {
            @Override
            public int compare(Ruling o1, Ruling o2) {
                return (int) Math.signum(o1.getTop() - o2.getTop());
            }});
        
        TreeMap<Point2D, Ruling[]> rv = new TreeMap<Point2D, Ruling[]>(new Comparator<Point2D>() {
            @Override
            public int compare(Point2D o1, Point2D o2) {
                if (o1.getY() > o2.getY()) return  1;
                if (o1.getY() < o2.getY()) return -1;
                if (o1.getX() > o2.getX()) return  1;
                if (o1.getX() < o2.getX()) return -1;
                return 0;
            }
        });
        
        for (Ruling h : horizontals) {
            sos.add(new SortObject(SOType.HLEFT, h.getLeft(), h));
            sos.add(new SortObject(SOType.HRIGHT, h.getRight(), h));
        }

//        System.out.println("Adding verticals");
        for (Ruling v : verticals) {
//            System.out.println("Vertical? " + v.vertical());
            sos.add(new SortObject(SOType.VERTICAL, v.getLeft(), v));
        }
        
        Collections.sort(sos, new Comparator<SortObject>() {
            @Override
            public int compare(SortObject a, SortObject b) {
                int rv;
                if (a.position == b.position) {
                    if (a.type == SOType.VERTICAL && b.type == SOType.HLEFT) {
                       rv = 1;     
                    }
                    else if (a.type == SOType.VERTICAL && b.type == SOType.HRIGHT) {
                       rv = -1;
                    }
                    else if (a.type == SOType.HLEFT && b.type == SOType.VERTICAL) {
                       rv = -1;
                    }
                    else if (a.type == SOType.HRIGHT && b.type == SOType.VERTICAL) {
                       rv = 1;
                     }
                    else {
                       rv = (int) Math.signum(a.position - b.position);
                    }
                }
                else {
                    return (int) Math.signum(a.position - b.position);
                }
                return rv;
            }
        });
        
        for (SortObject so : sos) {
            switch(so.type) {
            case VERTICAL:
                for (Map.Entry<Ruling, Boolean> h : tree.entrySet()) {
//                    System.out.format("h: %s v: %s\n", h.getKey(), so.ruling);
                    Point2D i = h.getKey().intersectionPoint(so.ruling);
                    if (i == null) {
                        continue;
                    }
                    rv.put(i, 
                           new Ruling[] { h.getKey().expand(PERPENDICULAR_PIXEL_EXPAND_AMOUNT), 
                                          so.ruling.expand(PERPENDICULAR_PIXEL_EXPAND_AMOUNT) });
                }
                break;
            case HRIGHT:
                tree.remove(so.ruling);
                break;
            case HLEFT:
                tree.put(so.ruling, true);
                break;
            }
        }
        
        return rv;
        
    }
    
    public static List<Ruling> collapseOrientedRulings(List<Ruling> lines) {
        ArrayList<Ruling> rv = new ArrayList<Ruling>();
        if (lines.size() == 0) {
            return rv;
        }
        Collections.sort(lines, new Comparator<Ruling>() {
            @Override
            public int compare(Ruling a, Ruling b) {
                return (int) (a.getPosition() != b.getPosition() ? a.getPosition() - b.getPosition() : a.getStart() - b.getStart());
            }
        });
        
        rv.add(lines.remove(0));
        for (Ruling next_line : lines) {
            Ruling last = rv.get(rv.size() - 1);
            // if current line colinear with next, and are "close enough": expand current line
            if (next_line.getPosition() == last.getPosition() && last.nearlyIntersects(next_line)) {
                last.setStart(next_line.getStart() < last.getStart() ? next_line.getStart() : last.getStart());
                last.setEnd(next_line.getEnd() < last.getEnd() ? last.getEnd() : next_line.getEnd());
            }
            else if (next_line.length() == 0) {
                continue;
            }
            else {
                rv.add(next_line);
            }
        }
        return rv;
    }
}
