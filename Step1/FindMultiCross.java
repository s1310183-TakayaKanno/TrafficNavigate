// ”渡邊涼介”全ての線分のペアで交差をチェックする機能のみ記載
for (int i = 0; i < M; i++) {
    for (int j = i + 1; j < M; j++) {
        
        // 線分Aの端点をAx,Ayへ
        int Ax1 = points[roads[i][0]][0];
        int Ax2 = points[roads[i][1]][0];
        int Ay1 = points[roads[i][0]][1];
        int Ay2 = points[roads[i][1]][1];
        
        // 線分Bの端点も
        int Bx1 = points[roads[j][0]][0];
        int Bx2 = points[roads[j][1]][0];
      　int By1 = points[roads[j][0]][1];
        int By2 = points[roads[j][1]][1];
        
        // findcross(線分A, 線分B) を実行
        double[] crossPoint = findcross(Ax1, Ay1, Ax2, Ay2, Bx1, By1, Bx2, By2);
        
        // 交点が存在すればリストに追加
        if (crossPoint != null) {
            intersections.add(crossPoint);
        }
    }
}
