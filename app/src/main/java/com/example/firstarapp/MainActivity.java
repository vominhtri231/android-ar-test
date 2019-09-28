package com.example.firstarapp;

import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private FloatingActionButton floatingActionButton;
    private boolean isTracking;
    private boolean isHitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        floatingActionButton = findViewById(R.id.floatingActionButton);

        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            arFragment.onUpdate(frameTime);
            onUpdate();
        });

        floatingActionButton.setOnClickListener(view -> addObject(Uri.parse("NOVELO_EARTH.sfb")));

        showFab(false);
    }

    /**
     * Updates the tracking state
     */
    private void onUpdate() {
        updateTracking();
        if (isTracking && updateHitTest()) {
            showFab(isHitting);
        }
    }

    /**
     * Simple function to show/hide our FAB
     *
     * @param enabled FAB enable or not
     */
    private void showFab(final boolean enabled) {
        floatingActionButton.setEnabled(enabled);
        if (enabled) {
            floatingActionButton.setSystemUiVisibility(View.VISIBLE);
        } else {
            floatingActionButton.setSystemUiVisibility(View.GONE);
        }
    }

    /**
     * Makes use of ARCore's camera state and returns true if the tracking state has changed
     */
    private void updateTracking() {
        Frame arFrame = arFragment.getArSceneView().getArFrame();
        isTracking = arFrame.getCamera().getTrackingState() == TrackingState.TRACKING;
    }

    /**
     * Performs frame.HitTest and returns if hit is changed or not
     *
     * @return hit is changed or not
     */
    private boolean updateHitTest() {
        final Frame arFrame = arFragment.getArSceneView().getArFrame();
        final boolean wasHitting = isHitting;
        isHitting = false;

        if (arFrame == null) {
            return wasHitting;
        }

        final Point point = this.getScreenCenter();
        final List<HitResult> hits = arFrame.hitTest(point.x, point.y);
        isHitting = hits.stream().anyMatch(hitResult ->
                trackCanPlaceObject(hitResult.getTrackable(), hitResult));
        return wasHitting != isHitting;
    }

    /**
     * This method takes in our 3D model and performs a hit test to determine where to place it
     *
     * @param model The Uri of our 3D sfb file
     */
    private void addObject(Uri model) {
        final Frame arFrame = arFragment.getArSceneView().getArFrame();
        Point point = getScreenCenter();
        if (arFrame != null) {
            final List<HitResult> hits = arFrame.hitTest(point.x, point.y);
            for (HitResult hit : hits) {
                if (trackCanPlaceObject(hit.getTrackable(), hit)) {
                    placeObject(arFragment, hit.createAnchor(), model);
                    break;
                }
            }
        }
    }

    private Point getScreenCenter() {
        View view = findViewById(android.R.id.content);
        return new Point(view.getWidth() / 2, view.getHeight() / 2);
    }

    private boolean trackCanPlaceObject(final Trackable trackable, final HitResult hit) {
        return trackable instanceof Plane
                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose());
    }

    /**
     * ses the ARCore anchor from the hitTest result and builds the Sceneform nodes.
     * It starts the asynchronous loading of the 3D model using the ModelRenderable builder.
     *
     * @param fragment our fragment
     * @param anchor   ARCore anchor from the hit test
     * @param model    our 3D model of choice
     */
    private void placeObject(ArFragment fragment, Anchor anchor, Uri model) {
        ModelRenderable.builder()
                .setSource(fragment.getContext(), model)
                .build()
                .thenAccept(action -> addNodeToScene(fragment, anchor, action))
                .exceptionally(action -> {
                    Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                    return null;
                });
    }

    /**
     * This method builds two nodes and attaches them to our scene
     * The Anchor nodes is positioned based on the pose of an ARCore Anchor. They stay positioned in the sample place relative to the real world.
     * The Transformable node is our Model
     * Once the nodes are connected we select the TransformableNode so it is available f
     *
     * @param fragment our fragment
     * @param anchor   ARCore anchor
     * @param model    our model created as a Sceneform Renderable
     */
    private void addNodeToScene(ArFragment fragment, Anchor anchor, ModelRenderable model) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        // TransformableNode means the user to move, scale and rotate the model
        TransformableNode transformableNode =
                new TransformableNode(fragment.getTransformationSystem());
        transformableNode.setRenderable(model);
        transformableNode.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        transformableNode.select();
    }
}
