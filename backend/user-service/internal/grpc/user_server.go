// Package grpcserver hosts user-service's INTERNAL gRPC API.
//
// This is the sync counterpart to the (now-removed) user.created event:
//   - auth-service calls CreateProfile during register, synchronously
//   - Strong consistency: register only succeeds if the profile is created
//   - Coupling: auth requires user-service to be reachable. Acceptable for
//     this low-volume flow; for high-volume flows we still use the event bus.
package grpcserver

import (
	"context"
	"errors"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	userv1 "github.com/thinh0704hcm/zalord/backend/user-service/proto/user/v1"
	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// UserServer implements UserInternalServer (generated from user.proto).
type UserServer struct {
	userv1.UnimplementedUserInternalServer
	profileService service.ProfileService
}

func NewUserServer(s service.ProfileService) *UserServer {
	return &UserServer{profileService: s}
}

func (s *UserServer) CreateProfile(ctx context.Context, req *userv1.CreateProfileRequest) (*userv1.CreateProfileResponse, error) {
	userId, err := uuid.Parse(req.UserId)
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, "invalid user_id: "+err.Error())
	}
	if req.DisplayName == "" {
		return nil, status.Error(codes.InvalidArgument, "display_name required")
	}
	if req.PhoneNumber == "" {
		return nil, status.Error(codes.InvalidArgument, "phone_number required")
	}

	err = s.profileService.CreateProfile(ctx, userId, req.DisplayName, req.PhoneNumber)
	if err != nil {
		// Idempotency: the repo does ON CONFLICT (user_id) DO NOTHING, so a
		// duplicate user_id is treated as success. But duplicate PHONE_NUMBER
		// (different user, same phone) IS a real conflict — surface it.
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			logger.Log.Warn("CreateProfile conflict", zap.Error(err))
			return nil, status.Error(codes.AlreadyExists, "phone_number already in use")
		}
		logger.Log.Error("CreateProfile failed", zap.Error(err))
		return nil, status.Error(codes.Internal, err.Error())
	}

	logger.Log.Info("grpc CreateProfile ok",
		zap.String("user_id", userId.String()),
		zap.String("display_name", req.DisplayName))

	return &userv1.CreateProfileResponse{
		UserId: req.UserId,
		// profile_id: we'd need a lookup to return the actual profile id.
		// Caller doesn't need it for current flows, leave empty for now.
	}, nil
}
